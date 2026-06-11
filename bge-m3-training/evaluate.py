import argparse
import json
from pathlib import Path

from prepare_data import validate_jsonl


def write_metrics(metrics_file: Path, metrics: dict) -> None:
    metrics_file.parent.mkdir(parents=True, exist_ok=True)
    metrics_file.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Write basic BGE-M3 fine-tuning evaluation metrics.")
    parser.add_argument("--train-data", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--metrics-file", required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    records = validate_jsonl(Path(args.train_data))
    metrics = {
        "records": records,
        "outputDir": args.output_dir,
        "recallAt5": 0.0,
        "recallAt10": 0.0,
        "mrrAt10": 0.0,
        "dryRun": args.dry_run,
        "note": "Dry-run metrics validate the data and command path. Replace with retrieval evaluation for production gates."
    }
    write_metrics(Path(args.metrics_file), metrics)
    print(json.dumps(metrics, ensure_ascii=False))


if __name__ == "__main__":
    main()
