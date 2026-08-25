"""Consumes cloud-cost-events, buffers into a DataFrame-ready form for the models."""
import json
import logging
from confluent_kafka import Consumer, KafkaError

from app.schemas.events import CloudCostEvent

logger = logging.getLogger(__name__)

TOPIC = "cloud-cost-events"


def make_consumer(bootstrap_servers="localhost:9092", group_id="clocost-ml") -> Consumer:
    conf = {
        "bootstrap.servers": bootstrap_servers,
        "group.id": group_id,
        "auto.offset.reset": "earliest",
        "enable.auto.commit": True,
    }
    consumer = Consumer(conf)
    consumer.subscribe([TOPIC])
    return consumer


def consume_loop(consumer: Consumer, on_event, poll_timeout=1.0):
    """
    on_event: callable(CloudCostEvent) -> None. Kept generic so tests can pass
    a list.append and production wires it to a buffer/DB write.
    """
    try:
        while True:
            msg = consumer.poll(poll_timeout)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                logger.error("consumer error: %s", msg.error())
                continue
            try:
                payload = json.loads(msg.value().decode("utf-8"))
                event = CloudCostEvent(**payload)
                on_event(event)
            except Exception as e:
                logger.error("failed to parse message: %s", e)
    except KeyboardInterrupt:
        pass
    finally:
        consumer.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    buffer = []
    c = make_consumer()
    logger.info("listening on %s ...", TOPIC)
    consume_loop(c, on_event=lambda e: buffer.append(e) or logger.info("received: %s", e.resource_id))
