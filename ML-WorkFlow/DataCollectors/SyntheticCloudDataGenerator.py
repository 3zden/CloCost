import boto3
import pandas as pd 
import numpy as np 
import random
from datetime import datetime,timedelta
class SyntheticCloudDataGenerator:
    def __init__(self, seed):
        np.random.seed(seed)
        random.seed(seed)
    def generate_synthetic_training_data(self,num_days=365,num_resources=50):
        end_date=datetime.now()
        start_date=end_date - timedelta(days=num_days)
        dates=pd.date_range(start=start_date,periods=num_days,freq='D')
        all_data=[]
        resource_types=[
            {'type':'EC2-Compute','base_cost':0.05,'variability':0.3},
            {'type':'EC2-Storage','base_cost':0.10,'variability':0.1},
            {'type':'RDS-Database','base_cost':0.2,'variability':0.2},
            {'type':'s3-Storage','base_cost':0.023,'variability':0.15},
            {'type':'Lambda-Compute','base_cost':0.0000002,'variability':0.5},
            {'type':'CloudFront-CDN','base_cost':0.085,'variability':0.4},
        ]
        for resource_idx in range(num_resources):
            resource_type=random.choice(resource_types)
            resource_id=f"{resource_type['type']}-{resource_idx:03d}"
            for day_idx,date in enumerate(dates):
                base_cost=resource_type['base_cost']
                
                day_of_week=date.weekday()
                hour=date.hour
                day_of_month=date.day
                if day_of_week<5:
                    base_cost*=1.5 #weekdays higher usage
                else:
                    base_cost*=0.3 #weekends lower usage
                if day_of_month >=25:
                    base_cost*=1.8 #end of month spike
                month=date.month
                if month in [10,11,12]:
                    base_cost*=1.4 #holiday season spike
                base_cost*=(1 + np.random.normal(0,resource_type['variability']))
                if np.random.rand()<0.05:
                    base_cost*=  np.random.uniform(3,10) #random spike
                usage=base_cost/resource_type['base_cost'] *100
                
                is_idle=usage<20
                is_underutilized=20<=usage<50
                is_optimized=50<=usage<80
                is_overutilized=usage>=80
                all_data.append({
                    'date':date,
                    'resource_id':resource_id,
                    'resource_type':resource_type['type'],
                    'cost':max(0,base_cost),
                    'usage_percent':min(100, max(0, usage)),
                    'day_of_week':day_of_week,
                    'day_of_month':day_of_month,
                    'month':month,
                    'is_weekend':day_of_week>=5,
                    'is_idle':is_idle,
                    'is_underutilized':is_underutilized,
                    'is_optimized':is_optimized,
                    'is_overutilized':is_overutilized,
                    'region':random.choice(['us-east-1','us-west-2','eu-west-1','ap-southeast-1']),
                    'environment':random.choice(['production','staging','development'])
                })
        df=pd.DataFrame(all_data)
        print(f"✓ Generated {len(df)} synthetic records")
        print(f"  • {num_resources} unique resources")
        print(f"  • {num_days} days of data")
        print(f"  • Total synthetic cost: ${df['cost'].sum():.2f}")
        print(f"  • Idle resources: {df['is_idle'].sum()}")
        print(f"  • Underutilized resources: {df['is_underutilized'].sum()}")
        print(f"  • Optimized resources: {df['is_optimized'].sum()}")
        print(f"  • Overutilized resources: {df['is_overutilized'].sum()}")
        print(f"  • Unique regions: {df['region'].nunique()}")
        print(f"  • Unique environments: {df['environment'].nunique()}")
        return df
if __name__=="__main__":
    generator=SyntheticCloudDataGenerator(seed=42)
    synthetic_data=generator.generate_synthetic_training_data(num_days=365,num_resources=100)
    synthetic_data.to_csv("synthetic_cloud_data.csv", index=False)
    print("✓ Successfully saved synthetic cloud data to synthetic_cloud_data.csv")
    print("\n=== SAMPLE SYNTHETIC DATA ===")
    print(synthetic_data.head())