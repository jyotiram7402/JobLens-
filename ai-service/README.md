# ai-service (placeholder)

No code yet. This directory reserves the boundary; the service is implemented in
the OCR/AI step of the roadmap.

## Future purpose

Turn a photograph of a real-world company sign into a confident company
identification:

1. Receive an uploaded image from the Spring Boot backend.
2. Run OCR to extract visible text (company name, signage, storefront branding).
3. Clean and normalise the extracted candidates.
4. Return ranked candidate company names with confidence scores.

## Responsibility boundary

The AI service is deliberately narrow. It is a **stateless inference service**.

It owns:

- image pre-processing
- OCR / text extraction
- candidate extraction and confidence scoring

It does **not** own:

- the database
- company verification against the canonical company record
- job data
- users, authentication or matching

All of that stays in the Spring Boot backend, which is the single source of
truth. The AI service never talks to PostgreSQL.

## How it will communicate with Spring Boot

Synchronous HTTP, backend → AI service, over an internal URL supplied by
environment variable (`AI_SERVICE_BASE_URL`). The browser never calls it
directly.

```
Browser → Spring Boot  POST /api/v1/scan        (multipart image)
          Spring Boot → ai-service  POST /ocr/extract
          ai-service  → Spring Boot  { candidates: [...] }
          Spring Boot resolves candidates against the company table
Browser ← Spring Boot  { company, confidence, jobs }
```

A message queue is explicitly **not** planned for V1. If OCR latency later makes
the synchronous call unacceptable, the roadmap revisits it then.

## Why AI/OCR is separated from the Java backend

1. **Ecosystem.** The mature OCR and vision libraries (Tesseract bindings,
   OpenCV, PaddleOCR, transformer models) are Python-first. Reimplementing or
   bridging them from Java costs more than one extra HTTP hop.
2. **Different resource profile.** OCR is CPU/memory heavy and bursty; the CRUD
   API is neither. Separating them lets each scale and be hosted independently,
   which matters on free tiers.
3. **Isolation of failure.** If the model service is down or slow, the rest of
   JobLens — browsing companies, jobs, tracking — keeps working.
4. **It is a genuine boundary, not architecture theatre.** This is the only part
   of V1 we are splitting out. Everything else remains a modular monolith.

## Planned stack

Python 3.12, FastAPI, Uvicorn, an open-source OCR engine. No paid AI APIs
without explicit approval.
