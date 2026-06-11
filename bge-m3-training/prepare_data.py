import argparse
import json
from pathlib import Path


def validate_record(record: dict, line_number: int) -> None:
    query = record.get("query")
    positives = record.get("pos")
    negatives = record.get("neg")
    if not isinstance(query, str) or not query.strip():
        raise ValueError(f"line {line_number}: query must be a non-empty string")
    if not isinstance(positives, list) or not positives or not all(isinstance(item, str) and item.strip() for item in positives):
        raise ValueError(f"line {line_number}: pos must be a non-empty string list")
    if negatives is not None and (not isinstance(negatives, list) or not all(isinstance(item, str) and item.strip() for item in negatives)):
        raise ValueError(f"line {line_number}: neg must be a string list when provided")


def validate_jsonl(path: Path) -> int:
    if not path.is_file():
        raise FileNotFoundError(f"training data not found: {path}")
    count = 0
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"line {line_number}: invalid JSON: {exc}") from exc
            validate_record(record, line_number)
            count += 1
    if count == 0:
        raise ValueError("training data must contain at least one record")
    return count


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate BGE-M3 fine-tuning JSONL data.")
    parser.add_argument("--input", required=True, help="Path to train.jsonl")
    args = parser.parse_args()
    count = validate_jsonl(Path(args.input))
    print(json.dumps({"records": count, "status": "ok"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
