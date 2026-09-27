# Intent: Chat client

## Goal
A small command-line program that takes one question as an argument, sends it to an AI model, prints the model's answer, and ends with a final line showing the model name and how many input/output tokens were used.

## Who it is for
You, for submission as the Week 2 introductory lab. Today there is nothing — the assignment is to demonstrate end-to-end intent, plan, and a working program that calls a real LLM API.

## Constraints
- Java, standard library only. No Maven, no Gradle, no third-party packages.
- The client talks to an OpenAI-compatible chat-completions endpoint (OpenRouter is one such provider). **Nothing secret is hardcoded in the source.** All configuration comes from environment variables:
  - API key: `OPENROUTER_API_KEY` (already set in your shell).
  - Base URL: `CHAT_BASE_URL`. OpenRouter's default is `https://openrouter.ai/api/v1`.
  - Model name: `CHAT_MODEL`. No fallback model is baked in — whatever `CHAT_MODEL` points to is what gets called.
- Must compile and run with a single `java` command (single-file source launch is fine).
- Question is passed as the first command-line argument (`argv[0]`).
- If a required env var is missing, the program prints a clear error to stderr naming the variable and exits non-zero. It does not silently fall back to a baked-in value.

## Not in scope
- Streaming output. The full answer prints at once after the API responds.
- Conversation history or multi-turn follow-ups. One invocation = one question, one answer.
- A web UI. CLI only.
- Automatic retries on transient errors. Failures exit non-zero with the error on stderr.
- Supporting more than one provider at a time. OpenRouter only.

## Success looks like
- Running `java ChatClient.java "What is the capital of France?"` prints an answer (e.g. `Paris`).
- The final line of output is exactly of the form `model=<name> in=<N> out=<N>`, where `<name>` is whatever the model-name env var currently points to and `<N>` values are the **same** numbers OpenRouter reports in its usage record for that request.
- Changing the model-name environment variable to a different OpenRouter model (e.g. from one Claude variant to another, or to any other supported model) and re-running produces an answer from the new model and a final line whose `model=` value reflects the change — no code edit required.
- If the API call fails (bad key, network error, refused request, missing env var), the program writes a useful message to stderr and exits non-zero — it does not print a fake answer and it does not silently fall back.

## Open questions
- Whether the file should live at the repo root as `ChatClient.java` for single-file launch, or as `src/ChatClient.java` compiled with `javac` then `java`. Either satisfies "single java command," but the intent does not yet pick.
- Behavior when the user passes no question argument (e.g. usage line vs. silent error). Not specified by the assignment, can be decided at spec time.
**Approved by:** Javier Romo, Sep 27, 2026