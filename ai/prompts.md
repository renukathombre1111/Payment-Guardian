# Payment Guardian — AI investigation prompts

Do not send the entire database to the model.

Give structured context only, then ask:

Analyze this proposed financial action. Identify risks, explain the evidence,
determine what information is missing, and recommend APPROVE, REVIEW, or BLOCK.
Do not invent evidence.

Expected JSON:

```json
{
  "decision": "REVIEW",
  "riskScore": 87,
  "confidence": 0.94,
  "reasons": [{"reason": "Bank account changed recently", "severity": "HIGH"}],
  "missingEvidence": ["Vendor confirmation of bank-account change"],
  "recommendation": "Verify new bank account before payment"
}
```

The Spring Boot service currently uses deterministic risk + templated explanations.
Set OPENAI_API_KEY later to swap in a live LLM without changing the product flow.

Safety: AI recommendation — human approval required. Never auto-execute payments.
