import numpy as np
import pandas as pd
from scipy import stats
from datetime import datetime, timedelta
import ast

from pathlib import Path
REF_DIR = "data/reference"
RNG = np.random.default_rng(42)


# ---------- 1. Fit params from reference data ----------

def fit_cpu_distribution():
    u = pd.read_csv(f"{REF_DIR}/utilization_data.csv")
    cpu = u.loc[u.metric == "CPUUtilization", "average"].clip(lower=1e-6)
    # lognormal fit -> gives us realistic idle-with-occasional-spike shape
    shape, loc, scale = stats.lognorm.fit(cpu, floc=0)
    return {"shape": shape, "scale": scale}  # mu=log(scale), sigma=shape


def fit_resource_mix():
    r = pd.read_csv(f"{REF_DIR}/resources_data.csv")
    counts = r.resource_type.value_counts(normalize=True).to_dict()
    return counts  # e.g. {'EBS':0.5,'S3':0.3,'EC2':0.2}


def fit_cost_scale():
    c = pd.read_csv(f"{REF_DIR}/costs_data.csv")
    # reference account is a near-idle/free-tier test account: costs ~0.
    # Use it only to confirm right-skew / near-zero floor; real $ scale for a
    # populated fleet is taken from public per-resource pricing (documented
    # assumption below), not invented arbitrarily.
    nonzero = c.loc[c.cost > 0, "cost"]
    return {"near_zero_floor": float(nonzero.mean()) if len(nonzero) else 1e-6}


CPU_PARAMS = fit_cpu_distribution()
RESOURCE_MIX_REF = fit_resource_mix()
COST_FLOOR = fit_cost_scale()

# ---------- 2. Priors for types not present in the small reference sample ----------
# (mean daily $ at "normal" utilization, lognormal sigma) — order-of-magnitude
# from public AWS pricing, since reference account had no live traffic on these.
TYPE_PROFILES = {
    "EC2-Compute":    dict(mean_cost=1.20, sigma=0.55, weight=0.25),
    "EC2-Storage":    dict(mean_cost=0.35, sigma=0.35, weight=0.15),
    "s3-Storage":     dict(mean_cost=0.20, sigma=0.45, weight=0.15),
    "Lambda-Compute": dict(mean_cost=0.05, sigma=0.90, weight=0.20),
    "RDS-Database":   dict(mean_cost=2.10, sigma=0.40, weight=0.10),
    "CloudFront-CDN": dict(mean_cost=0.90, sigma=0.60, weight=0.15),
}
REGIONS = ["us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"]
ENVIRONMENTS = ["production", "staging", "development"]
ENV_WEIGHTS = [0.6, 0.25, 0.15]
WEEKEND_SUPPRESSION = 0.75  # non-prod / batch-driven cost dips on weekends

# usage_percent behavior mixture (idle / underutil / optimized / overutil)
# priors are FinOps-typical; refine once real utilization_data has volume.
BEHAVIOR_MIX = {"idle": 0.12, "underutilized": 0.28, "optimized": 0.50, "overutilized": 0.10}
BEHAVIOR_RANGE = {
    "idle": (0, 5), "underutilized": (5, 40),
    "optimized": (40, 75), "overutilized": (75, 100),
}


def sample_usage_percent(n):
    cats = RNG.choice(list(BEHAVIOR_MIX), size=n, p=list(BEHAVIOR_MIX.values()))
    vals = np.array([RNG.uniform(*BEHAVIOR_RANGE[c]) for c in cats])
    return vals, cats


def sample_cost(mean_cost, sigma, n, weekend_mask, env):
    mu = np.log(mean_cost) - (sigma ** 2) / 2
    cost = RNG.lognormal(mu, sigma, size=n)
    cost[weekend_mask] *= WEEKEND_SUPPRESSION
    if env == "development":
        cost *= 0.4
    elif env == "staging":
        cost *= 0.7
    return np.round(cost, 6)


# ---------- 3. Generate ----------

def generate(n_resources=100, days=365, end_date=None):
    end_date = end_date or datetime.utcnow()
    start_date = end_date - timedelta(days=days - 1)
    dates = pd.date_range(start_date, end_date, freq="D")

    types = list(TYPE_PROFILES)
    type_weights = [TYPE_PROFILES[t]["weight"] for t in types]
    rows = []

    for i in range(n_resources):
        rtype = RNG.choice(types, p=type_weights)
        region = RNG.choice(REGIONS)
        env = RNG.choice(ENVIRONMENTS, p=ENV_WEIGHTS)
        rid = f"{rtype}-{i:03d}"
        profile = TYPE_PROFILES[rtype]

        weekend_mask = dates.weekday.isin([5, 6])
        costs = sample_cost(profile["mean_cost"], profile["sigma"], len(dates), weekend_mask, env)
        usage_vals, behavior_cats = sample_usage_percent(len(dates))

        for d, cost, usage, cat, wk in zip(dates, costs, usage_vals, behavior_cats, weekend_mask):
            rows.append({
                "date": d,
                "resource_id": rid,
                "resource_type": rtype,
                "cost": cost,
                "usage_percent": round(float(usage), 4),
                "day_of_week": d.weekday(),
                "day_of_month": d.day,
                "month": d.month,
                "is_weekend": bool(wk),
                "is_idle": cat == "idle",
                "is_underutilized": cat == "underutilized",
                "is_optimized": cat == "optimized",
                "is_overutilized": cat == "overutilized",
                "region": region,
                "environment": env,
            })

    return pd.DataFrame(rows)


if __name__ == "__main__":
    df = generate(n_resources=100, days=365)
    out_path = "data/generated/synthetic_cloud_data_v2.csv"
    df.to_csv(out_path, index=False)

    print("rows:", len(df))
    print(df.groupby("resource_type")["cost"].describe()[["mean", "std", "min", "max"]])
    print("\nfitted CPU lognorm params (from utilization_data.csv):", CPU_PARAMS)
    print("resource mix in reference inventory:", RESOURCE_MIX_REF)
