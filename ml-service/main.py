from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, conlist
import numpy as np
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import IsolationForest
from typing import List

app = FastAPI(title="Finance ML Service", version="0.1.0")

# --- CORS (tighten origins later to your domains) ---
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # e.g. ["https://your-frontend.vercel.app", "https://your-backend.onrender.com"]
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health():
    return {"ok": True}

@app.get("/")
def root():
    return {"message": "ML service is running!"}

# --------- Forecast ----------
class MonthDatum(BaseModel):
    year: int
    month: int
    income: float
    expense: float

class SavingsPayload(BaseModel):
    months: conlist(MonthDatum, min_length=1)

@app.post("/predict/savings")
def predict_savings(payload: SavingsPayload):
    # y = income - expense; simple linear regression on time index
    y = np.array([m.income - m.expense for m in payload.months], dtype=float)
    X = np.arange(len(y)).reshape(-1, 1)
    if len(y) >= 2 and len(set(y)) > 1:
        model = LinearRegression().fit(X, y)
        pred = float(model.predict([[len(y)]])[0])
    else:
        pred = float(np.mean(y))
    return {"next_month_savings": round(pred, 2), "history": y.tolist()}

# Optional: legacy shape support if any caller sends {"history":[...]}
class SavingsLegacy(BaseModel):
    history: List[float]

@app.post("/predict/savings_legacy")
def predict_savings_legacy(payload: SavingsLegacy):
    y = np.array(payload.history, dtype=float)
    if y.size == 0:
        return {"next_month_savings": 0.0, "history": []}
    X = np.arange(len(y)).reshape(-1, 1)
    if len(y) >= 2 and len(set(y)) > 1:
        pred = float(LinearRegression().fit(X, y).predict([[len(y)]])[0])
    else:
        pred = float(np.mean(y))
    return {"next_month_savings": round(pred, 2), "history": y.tolist()}

# --------- Expense anomaly ----------
class AnomalyReq(BaseModel):
    history: list[float] = []
    candidate: float

class AnomalyResp(BaseModel):
    is_anomaly: bool
    score: float
    method: str

@app.post("/anomaly/expense", response_model=AnomalyResp)
def anomaly(req: AnomalyReq):
    hist = np.array(req.history, dtype=float)
    x = float(req.candidate)

    # Small-sample heuristic (bootstrapping)
    if len(hist) < 3:
        mu = float(np.mean(hist)) if len(hist) else 0.0
        is_anom = (x >= 5000) or (len(hist) and x > 1.8 * mu)
        score = (x - mu) / (mu or 1.0)
        return {"is_anomaly": bool(is_anom), "score": float(score), "method": "small-sample"}

    # IsolationForest if enough data & variability
    if len(hist) >= 8 and len(np.unique(hist)) >= 3:
        model = IsolationForest(contamination=0.1, random_state=42)
        model.fit(hist.reshape(-1, 1))
        score = -model.decision_function([[x]])[0]  # higher => more anomalous
        pred = model.predict([[x]])[0]              # -1 => anomaly
        return {"is_anomaly": bool(pred == -1), "score": float(score), "method": "isoforest"}

    # Robust Z with MAD
    median = float(np.median(hist))
    mad = float(np.median(np.abs(hist - median)))
    if mad > 0:
        z_robust = abs(x - median) / (1.4826 * mad)
        return {"is_anomaly": bool(z_robust >= 2.5), "score": float(z_robust), "method": "mad-z"}

    # Flat data fallback: ratio check
    ratio = (x / (median or 1.0))
    is_anom = (ratio >= 1.8) or (x >= 5000)
    return {"is_anomaly": bool(is_anom), "score": float(ratio), "method": "ratio"}
