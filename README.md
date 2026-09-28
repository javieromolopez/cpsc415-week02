# OpenRouter Chat Client

**What it does and how to run it:**
This is a single-file Java command-line chat client that sends a user question to an LLM via the OpenRouter API. It prints the model's answer and a final line displaying the model name and token usage. 

To run it, set the following environment variables in PowerShell (never commit your actual API key to GitHub):
`$env:OPENROUTER_API_KEY="your_key_here"`
`$env:CHAT_BASE_URL="https://openrouter.ai/api/v1"`
`$env:CHAT_MODEL="minimax/minimax-m3"`

Then execute the file directly:
`java ChatClient.java "Your question here"`

**Intent Corrections:**
During the intent draft phase, I corrected two open questions to define the application's boundaries:
1. I specified that `ChatClient.java` should live at the repository root to allow for a single-file launch without a separate `javac` compilation step.
2. I defined the missing-argument behavior: if no question is provided, the program prints a clear usage line to stderr and exits gracefully rather than crashing.

**Code Explanation:**
"static String buildRequestBody(String model, String question) {
        return "{\"model\":\"" + jsonEscape(model) + "\","
             + "\"messages\":[{\"role\":\"system\",\"content\":\"Answer like a pirate\"},"
             + "{\"role\":\"user\",\"content\":\"" + jsonEscape(question) + "\"}],"
             + "\"max_tokens\":100}";
  }" 
This block of code is which enables the system to send a chat request to the OpenRouter API. As it is not allowed to use external JSOn libraries, the agent had to build the formatted string to send the chat request.

**Model Comparison:**
I asked the question "In one sentence, what is a context window?" to two different models:
*   **minimax/minimax-m3:** A not very effective pirate like description, making a comparison with the sea as the tokens and the abyss swallowing those tokens. The OpenRouter Activity page showed a cost of $0.00011912 for 184 input and 80 output tokens.
*   **anthropic/claude-sonnet-5:** Meanwhile, the description of this also pirate like desciption was more descriptive and assertive, as it related a treasure chest with the context window containing words that the model can holt all at once (setting a limit). The OpenRouter Activity page showed a cost of $0.001054 for 27 input and 100 output tokens.
*(Note: I did not test a local model).*
