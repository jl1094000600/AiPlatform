import argparse
import json
import subprocess
import sys
from pathlib import Path

from evaluate import write_metrics
from prepare_data import validate_jsonl


def build_flag_embedding_command(args: argparse.Namespace) -> list[str]:
    command = [
        sys.executable,
        "-m",
        "FlagEmbedding.finetune.embedder.encoder_only.m3",
        "--model_name_or_path",
        args.model_path,
        "--train_data",
        args.train_data,
        "--output_dir",
        args.output_dir,
        "--learning_rate",
        str(args.learning_rate),
        "--num_train_epochs",
        str(args.epochs),
        "--query_max_len",
        str(args.query_max_len),
        "--passage_max_len",
        str(args.passage_max_len),
        "--train_group_size",
        str(args.train_group_size),
        "--same_dataset_within_batch",
        "true",
        "--normalize_embeddings",
        "true",
    ]
    if args.unified_finetuning:
        command.extend(["--unified_finetuning", "true"])
    if args.device:
        command.extend(["--device", args.device])
    return command


def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune BGE-M3 with FlagEmbedding.")
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--train-data", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--epochs", type=int, default=1)
    parser.add_argument("--learning-rate", type=float, default=1e-5)
    parser.add_argument("--query-max-len", type=int, default=256)
    parser.add_argument("--passage-max-len", type=int, default=512)
    parser.add_argument("--train-group-size", type=int, default=4)
    parser.add_argument("--metrics-file", required=True)
    parser.add_argument("--unified-finetuning", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--device", default="")
    args = parser.parse_args()

    train_path = Path(args.train_data)
    output_dir = Path(args.output_dir)
    metrics_file = Path(args.metrics_file)
    records = validate_jsonl(train_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    command = build_flag_embedding_command(args)
    snapshot = {
        "jobId": args.job_id,
        "records": records,
        "modelPath": args.model_path,
        "trainData": args.train_data,
        "outputDir": args.output_dir,
        "epochs": args.epochs,
        "learningRate": args.learning_rate,
        "queryMaxLen": args.query_max_len,
        "passageMaxLen": args.passage_max_len,
        "trainGroupSize": args.train_group_size,
        "unifiedFinetuning": args.unified_finetuning,
        "dryRun": args.dry_run,
        "command": command,
    }
    Path(args.output_dir, "training_config.json").write_text(
        json.dumps(snapshot, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(json.dumps(snapshot, ensure_ascii=False, indent=2))
    if args.dry_run:
        metrics = {
            "records": records,
            "recallAt5": 0.0,
            "recallAt10": 0.0,
            "mrrAt10": 0.0,
            "dryRun": True,
            "outputDir": args.output_dir,
        }
        write_metrics(metrics_file, metrics)
        return

    result = subprocess.run(command, check=False)
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    metrics = {
        "records": records,
        "recallAt5": 0.0,
        "recallAt10": 0.0,
        "mrrAt10": 0.0,
        "dryRun": False,
        "outputDir": args.output_dir,
        "note": "Training finished. Run retrieval evaluation to replace placeholder metrics."
    }
    write_metrics(metrics_file, metrics)


if __name__ == "__main__":
    main()
