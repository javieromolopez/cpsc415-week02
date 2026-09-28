| Check | Expected | Observed | Pass/fail |
|---|---|---|---|
| Question through OpenRouter | An answer and a usage line | The program displayed an answer followed by the model and also the token counts | Pass |
| Usage record matches | Same model; same or close token counts | From the OpenRouter dashboard I could confirm that the minimax/minimax-m3 model matched the token counts that were printed in my terminal | Pass |
| System prompt changed | Answer style changes accordingly | The model started answering in a like pirate persona manner | Pass |
| `max_tokens` = 20 | Truncated or empty answer; tokens still billed | The model stopped printing the sentence answer and only printed the model and the token counts | Pass |
| Model swapped (step 4) | Different model name in usage; answer may differ |anthropic/claude-sonnet-5: Arrr, matey, a context window be the treasure chest o' words a ship's AI brain can hold in its noggin at once, settin' the limit on how much o' yer scroll it can read afore it starts forgettin' the
model=anthropic/claude-sonnet-5 in=27 out=100; minimax/minimax-m3: Arrr, a context window be the great salty sea o' tokens that yer AI matey can spy through at once, beyond which all be swallowed by the abyss! ????
model=minimax/minimax-m3 in=184 out=80 | Pass |
| Local model (optional) | Answer from localhost; no OpenRouter entry | | |
