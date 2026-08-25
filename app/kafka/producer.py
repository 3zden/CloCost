"""Publishes InsightEvent objects to insight-events."""
import json
import logging
from confluent_kafka import Producer

from app.schemas.events import InsightEvent

logger = logging.getLogger(__name__)

TOPIC = "insight-events"


def make_producer(bootstrap_servers="localhost:9092") -> Producer:
    return Producer({"bootstrap.servers": bootstrap_servers})


def _delivery_report(err, msg):
    if err is not None:
        logger.error("delivery failed: %s", err)
    else:
        logger.debug("delivered to %s [%d]", msg.topic(), msg.partition())


def publish_insight(producer: Producer, event: InsightEvent, key: str | None = None):
    producer.produce(
        TOPIC,
        key=key,
        value=event.model_dump_json(),
        callback=_delivery_report,
    )
    producer.poll(0)  # trigger delivery callbacks without blocking


def flush(producer: Producer, timeout=10):
    producer.flush(timeout)


if __name__ == "__main__":
    from datetime import datetime
    from app.schemas.events import InsightType, Severity, AnomalyDetails

    logging.basicConfig(level=logging.INFO)
    p = make_producer()
    test_event = InsightEvent(
        insight_type=InsightType.ANOMALY,
        severity=Severity.HIGH,
        confidence=0.92,
        message="test insight",
	generated_at=datetime.now(timezone.utc),
        details=AnomalyDetails(resource_id="EC2-Compute-000", cost=12.5, expected_cost=3.2, zscore=5.1),
    )
    publish_insight(p, test_event, key=test_event.details.resource_id)
    flush(p)
    logger.info("published test event")
