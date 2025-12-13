from fastapi import FastAPI
from pydantic import BaseModel, conlist
from typing import List, Dict, Any
import numpy as np
from sklearn.linear_model import LinearRegression
from pydantic import BaseModel
import numpy as np
from sklearn.ensemble import IsolationForest

app = FastAPI(title="Finance ML Service", version="0.1.0")

class MonthDatum(BaseModel):
    year: int
    month: int
    income: float
    expense: float

class SavingsPayload(BaseModel):
    months: conlist(MonthDatum, min_length=1)

@app.post("/predict/savings")
def predict_savings(payload: SavingsPayload):
    # y = income - expense; simple linear regression over time index
    y = np.array([m.income - m.expense for m in payload.months], dtype=float)
    X = np.arange(len(y)).reshape(-1, 1)
    if len(y) >= 2 and len(set(y)) > 1:
        model = LinearRegression().fit(X, y)
        next_idx = np.array([[len(y)]])
        pred = model.predict(next_idx)[0]
    else:
        # fallback: average
        pred = float(np.mean(y))
    return {"next_month_savings": round(float(pred), 2), "history": y.tolist()}

    @app.get("/")
    def root():
        return {"message": "ML service is running!"}

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

    # --- Small-sample heuristic (very helpful during bootstrapping) ---
    if len(hist) < 3:
        mu = float(np.mean(hist)) if len(hist) else 0.0
        # Treat large absolute spends or big jumps as anomalies
        is_anom = (x >= 5000) or (len(hist) and x > 1.8 * mu)
        score = (x - mu) / (mu or 1.0)
        return {"is_anomaly": bool(is_anom), "score": float(score), "method": "small-sample"}

    # --- Try IsolationForest when we have enough data & variability ---
    if len(hist) >= 8 and len(np.unique(hist)) >= 3:
        model = IsolationForest(contamination=0.1, random_state=42)
        model.fit(hist.reshape(-1, 1))
        # Higher score -> more anomalous (we negate decision_function)
        score = -model.decision_function([[x]])[0]
        pred = model.predict([[x]])[0]  # -1 = anomaly
        return {"is_anomaly": bool(pred == -1), "score": float(score), "method": "isoforest"}

    # --- Robust Z using MAD (works even when std==0) ---
    median = float(np.median(hist))
    mad = float(np.median(np.abs(hist - median)))
    if mad > 0:
        z_robust = abs(x - median) / (1.4826 * mad)
        return {"is_anomaly": bool(z_robust >= 2.5), "score": float(z_robust), "method": "mad-z"}

    # --- Flat data: ratio check as last resort ---
    ratio = (x / (median or 1.0))
    is_anom = (ratio >= 1.8) or (x >= 5000)
    return {"is_anomaly": bool(is_anom), "score": float(ratio), "method": "ratio"}

