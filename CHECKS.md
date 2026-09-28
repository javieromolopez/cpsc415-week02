| Check | Expected | Observed | Pass/fail |
| :--- | :--- | :--- | :--- |
| Question through OpenRouter | An answer and a usage line | The program displayed an answer followed by the model name and token counts. | Pass |
| Usage record matches | Same model; same or close token counts | From the OpenRouter dashboard, confirmed that minimax/minimax-m3 matched the token counts printed in the terminal. | Pass |
| System prompt changed | Answer style changes accordingly | The model started answering in a pirate persona. | Pass |
| `max_tokens` = 20 | Truncated or empty answer; tokens still billed | The model stopped printing the sentence answer and only printed the model name and 20 output tokens. | Pass |
| Model swapped (step 4) | Different model name in usage; answer may differ | **Claude Sonnet 5:** "Arrr, matey, a context window be the treasure chest o' words a ship's AI brain can hold in its noggin at once..." (`model=anthropic/claude-sonnet-5 in=27 out=100`)<br><br>**MiniMax M3:** "Arrr, a context window be the great salty sea o' tokens that yer AI matey can spy through at once..." (`model=minimax/minimax-m3 in=184 out=80`) | Pass |
| Local model (optional) | Answer from localhost; no OpenRouter entry | Skipped (optional check). | N/A |
