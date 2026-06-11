# BGE-M3 Training

Local fine-tuning workspace for BGEM3 retrieval models.

## Data Format

Training data is JSONL. Each line must contain:

```json
{"query":"question","pos":["positive passage"],"neg":["hard negative passage"]}
```

## Dry Run

```powershell
python bge-m3-training/train_bgem3.py `
  --job-id MT_LOCAL `
  --model-path BAAI/bge-m3 `
  --train-data bge-m3-training/data/train.jsonl `
  --output-dir bge-m3-training/output/bge-m3-ft `
  --epochs 1 `
  --learning-rate 1e-5 `
  --query-max-len 256 `
  --passage-max-len 512 `
  --train-group-size 4 `
  --metrics-file bge-m3-training/runs/MT_LOCAL/metrics.json `
  --dry-run
```

Install dependencies from `requirements.txt` before real training. Real training writes output under `output/`; logs and metrics live under `runs/`.
