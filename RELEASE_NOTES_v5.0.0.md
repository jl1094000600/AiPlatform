# AI Platform v5.0.0

## Version Positioning

v5.0.0 extends the existing "fill requirement -> generate PRD" workflow with multimodal requirement input. It does not replace or restructure the existing PRD pipeline.

## Delivered Scope

- Text, multiple image files, multiple audio files, and browser audio recording are accepted as requirement inputs.
- Images are processed with OCR and semantic understanding through the platform-configured default `VISION` model.
- Audio is transcribed through the platform-configured default `ASR` model, including Chinese, English, and mixed speech when supported by the selected model.
- Each attachment is parsed asynchronously and has an independent status, retry, edit, and delete lifecycle.
- Parsed attachment content can be edited before it is merged with the original text into a confirmed requirement draft.
- The confirmed draft continues through the existing automation pipeline to generate the PRD.
- Raw attachments are retained by the platform and removed by the configured retention cleanup task.
- Model management supports `CHAT`, `VISION`, and `ASR` capability types with one enabled default model per capability and tenant.

## Architecture Boundary

Image understanding and audio transcription are built-in platform capabilities. `intent-agent` and `image-agent` remain optional business agents and are not required for multimodal requirement input or PRD generation.

## Deferred Scope

Video and document parsing are reserved for a later phase.

## Verification

- Consumer frontend API tests passed.
- Consumer frontend production build passed using an isolated output directory.
- Administration frontend business and model-capability tests passed.
- Administration frontend production build passed using an isolated output directory.
- Backend model-capability focused tests passed in the available local Java test harness.
- Full backend Maven verification requires a runnable JDK in the execution environment.
