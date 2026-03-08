from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import re

app = FastAPI()

class ReviewContent(BaseModel):
    content: str

DIFFICULTY_KEYWORDS = {
    "hard": ["어려움", "복잡함", "전공자", "심화", "난해", "고급"],
    "easy": ["쉬움", "입문", "기초", "초보", "만만", "간단", "입문자"]
}

@app.post("/analyze-difficulty")
async def analyze_difficulty(review: ReviewContent):
    content = review.content
    if not content:
        return {"difficulty_score": 3, "level": "보통"}
    
    score = 3
    
    # 키워드 매칭
    hard_count = sum(1 for kw in DIFFICULTY_KEYWORDS["hard"] if kw in content)
    easy_count = sum(1 for kw in DIFFICULTY_KEYWORDS["easy"] if kw in content)
    
    # 5단계 수치화 로직
    if hard_count > 0:
        score += min(2, hard_count)
    if easy_count > 0:
        score -= min(2, easy_count)
        
    score = max(1, min(5, score))
    
    levels = {1: "매우 쉬움", 2: "쉬움", 3: "보통", 4: "어려움", 5: "매우 어려움"}
    
    return {
        "difficulty_score": score,
        "level": levels.get(score, "보통"),
        "analysis": {
            "hard_keywords_found": [kw for kw in DIFFICULTY_KEYWORDS["hard"] if kw in content],
            "easy_keywords_found": [kw for kw in DIFFICULTY_KEYWORDS["easy"] if kw in content]
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
