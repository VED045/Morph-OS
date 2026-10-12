# On-device AI — Android setup and tested models

The phone runs inference itself. There is no Ollama PC dependency and no model call through a desktop relay. MorphOS uses the pinned [LiteRT-LM Android runtime](https://github.com/google-ai-edge/LiteRT-LM) 0.13.1 with its CPU backend in a private process.

## Tested useful fallback

On 12 October 2026, the Android 16/API 36 x86_64 emulator imported and checked **Qwen3 0.6B dynamic INT4**. The local-only request `Give a timer of three minutes` generated a strict, validated TIMER proposal with 180 seconds, title Focus and ROUNDED shape. Explicit Add placed its functional 03:00 timer on home. This is one successful request, not a broad accuracy benchmark or a physical phone performance claim.

Use this exact tested artifact rather than assuming every current export works:

- Repository: [litert-community/Qwen3-0.6B](https://huggingface.co/litert-community/Qwen3-0.6B).
- Revision: `8414150f2e9dcc82449bcc9c5abc404b399a4d06`.
- File: `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`.
- [Pinned download](https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8414150f2e9dcc82449bcc9c5abc404b399a4d06/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm).
- Size: 344,437,808 bytes, approximately 328 MiB.
- SHA-256: `e3e290109da4388d65a17510a0c66af91c8039f52d2c465868dbc43c09a776cf`.
- Source license: Apache-2.0; retain applicable model license/notice terms for redistribution. The APK and source do not redistribute weights.

The current model card documents newer artifact changes requiring newer runtimes. A newer model with the same name can be incompatible with 0.13.1. The pinned revision above is what this integration actually tested.

## Install on your phone

1. Download the model to Android Downloads, or copy it there from a computer. Keep enough space for the downloaded file plus a private imported copy and working memory. Import reserves at least 128 MiB of free storage and rejects files over 8 GiB.
2. Open MorphOS → star / AI design studio → On-device AI → Import model.
3. Choose the .litertlm file in Android's document picker. Then tap Check model.
4. Select On phone, request a simple bounded design or widget edit, inspect its preview and apply. Backup instead falls back to this installed model after a remote request fails.
5. Use Delete model to remove the private copy; delete the Downloads copy separately if desired. App uninstallation removes its private model and metadata.

No model is silently downloaded or bundled. Import does not accept source-site licenses on your behalf. Native libraries cover arm64-v8a and x86_64; sufficient RAM, compatible model graphs and device support are required. Check model proves loading and response generation, not proposal accuracy.

## Other models and limits

[SmolLM2 135M](https://huggingface.co/litert-community/SmolLM2-135M-Instruct) loaded and generated during the initial runtime smoke test, but its widget output failed validation. It is useful as a tiny runtime diagnostic, not the recommended design backup. Its checked file SHA-256 was `ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37`.

[Gemma 3 1B](https://huggingface.co/litert-community/Gemma3-1B-IT) is an optional compatible-artifact candidate, subject to accepting its source license. It has not been downloaded or tested here. Check the exact export's runtime requirements before import.

Small local models have lower reasoning and instruction-following capacity than Nemotron Ultra. The local adapter permits declarative theme patches or registered widget specifications, not code execution or arbitrary actions. Text models receive image-derived colors rather than image pixels. It applies strict envelopes/types/bounds and revision checks. Invalid output never applies automatically; offline templates still work.

Model requests include only the user prompt and declarative current theme (for design). Contacts, calendar contents, notification/history records and app-use history are excluded. On phone skips the remote text route. Local image analysis can sample colors and ask the text model to refine the theme. The optional GPU vision switch sends image pixels to an imported compatible multimodal model; this path is implemented but not physically validated. Remote vision remains available when separately configured.

Evidence: [Qwen runtime/proposal result](test-results/qwen-model-api36.json), [initial small-model result](test-results/local-model-api36.json).

## Larger local models

[Qwen3 1.7B](https://huggingface.co/litert-community/Qwen3-1.7B) is a larger free text-model option to evaluate, but its exact export must support LiteRT-LM 0.13.1; it has not been benchmarked here. [Gemma 3n](https://ai.google.dev/gemma/docs/gemma-3n) supports multimodal on-device use, subject to its license and compatible export. Import a compatible artifact and enable Image-capable model only for a multimodal model. Allow substantially more RAM/storage than the tested 0.6B model. Do not assume a latest model export is compatible with this pinned runtime. See [LiteRT-LM Android multimodality](https://developers.google.com/edge/litert-lm/android).
