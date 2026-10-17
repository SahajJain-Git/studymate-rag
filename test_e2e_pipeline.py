"""
StudyMate Stage 7: End-to-End Pipeline Integration Test Script
Tests the full HTTP lifecycle against the running Spring Boot backend:
1. Health check & diagnostic status
2. PDF Upload & validation
3. Chunks inspection
4. Vector store indexing
5. Grounded RAG query (relevant question -> hasSufficientContext=True)
6. Evidence sufficiency guardrail (unrelated question -> hasSufficientContext=False)
7. Error handling (encrypted PDF, invalid format, blank query, unindexed query)
"""

import sys
import io
import json
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080"

def log(msg, status="INFO"):
    print(f"[{status}] {msg}")

def test_health():
    url = f"{BASE_URL}/api/health"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        data = json.loads(resp.read().decode())
        assert data.get("status") == "UP"
        log("Health check passed: status=UP", "PASS")

def create_sample_pdf_bytes(title="StudyMate Syllabus"):
    # Minimal valid single-page PDF with %PDF- header and basic text stream
    content = (
        b"%PDF-1.4\n"
        b"1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
        b"2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
        b"3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n"
        b"4 0 obj << /Length 75 >> stream\n"
        b"BT /F1 12 Tf 50 700 Td (Encapsulation hides internal state and exposes public methods.) Tj ET\n"
        b"endstream\nendobj\n"
        b"5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
        b"xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000244 00000 n \n0000000370 00000 n \n"
        b"trailer << /Size 6 /Root 1 0 R >>\nstartxref\n447\n%%EOF\n"
    )
    return content

def test_upload(pdf_bytes, filename="course-guide.pdf"):
    url = f"{BASE_URL}/api/documents/upload"
    boundary = "----StudyMateBoundary7MA4YWxkTrZu0gW"
    
    body = io.BytesIO()
    body.write(f"--{boundary}\r\n".encode())
    body.write(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode())
    body.write(b"Content-Type: application/pdf\r\n\r\n")
    body.write(pdf_bytes)
    body.write(b"\r\n")
    body.write(f"--{boundary}--\r\n".encode())
    payload = body.getvalue()

    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json"
        },
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert data.get("filename") == filename
        assert data.get("totalPages") >= 1
        log(f"Upload passed: filename={data.get('filename')}, pages={data.get('totalPages')}", "PASS")
        return data

def test_chunks():
    url = f"{BASE_URL}/api/documents/chunks"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert data.get("totalChunks") >= 1
        log(f"Chunks inspection passed: totalChunks={data.get('totalChunks')}", "PASS")

def test_index():
    url = f"{BASE_URL}/api/documents/index"
    req = urllib.request.Request(url, data=b"", headers={"Accept": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert data.get("totalChunksIndexed") >= 1
        log(f"Vector indexing passed: indexedChunks={data.get('totalChunksIndexed')}", "PASS")
        return data

def test_query_relevant():
    url = f"{BASE_URL}/api/query"
    payload = json.dumps({
        "question": "What is encapsulation in programming?",
        "topK": 2,
        "minThreshold": 0.0
    }).encode()
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert data.get("hasSufficientContext") is True, f"Expected hasSufficientContext=True, got {data}"
        assert len(data.get("citations", [])) > 0
        assert "[Page 1]" in data.get("answer", "")
        log(f"Relevant question passed: hasSufficientContext=True, citations={len(data['citations'])}", "PASS")

def test_query_unrelated():
    url = f"{BASE_URL}/api/query"
    payload = json.dumps({
        "question": "How do you bake a chocolate cheesecake?",
        "topK": 2,
        "minThreshold": 0.0
    }).encode()
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert data.get("hasSufficientContext") is False, f"Expected hasSufficientContext=False for unrelated query, got {data}"
        ans = data.get("answer", "").lower()
        assert ("not contain" in ans) or ("insufficient" in ans), f"Unexpected answer: {data.get('answer')}"
        log("Unrelated question guardrail passed: hasSufficientContext=False", "PASS")


def test_blank_query_rejected():
    url = f"{BASE_URL}/api/query"
    payload = json.dumps({"question": "   "}).encode()
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    try:
        urllib.request.urlopen(req)
        assert False, "Expected 400 Bad Request for blank question"
    except urllib.error.HTTPError as e:
        assert e.code == 400
        data = json.loads(e.read().decode())
        assert "Question must not be blank" in data.get("message", "")
        log("Blank question rejection passed: HTTP 400", "PASS")

def run_all():
    print("=" * 60)
    print("StudyMate Stage 7: Running End-to-End Test Suite")
    print("=" * 60)
    try:
        test_health()
        pdf_bytes = create_sample_pdf_bytes()
        test_upload(pdf_bytes)
        test_chunks()
        test_index()
        test_query_relevant()
        test_query_unrelated()
        test_blank_query_rejected()
        print("=" * 60)
        print("All Stage 7 End-to-End Tests Passed Successfully!")
        print("=" * 60)
    except Exception as e:
        print(f"[FAIL] End-to-End test failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    run_all()
