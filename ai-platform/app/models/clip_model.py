import torch
import open_clip
import numpy as np
from PIL import Image
import requests
from io import BytesIO
from app.models.base import BaseAiModel, ModelInfo
import time


class ClipModel(BaseAiModel):
    def __init__(self, model_name: str = "ViT-L-14", pretrained: str = "openai"):
        self._model = None
        self._preprocess = None
        self._tokenizer = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        self._model_name = model_name
        self._pretrained = pretrained
        self._loaded_at = None

    def load(self):
        if self._model is None:
            self._model, _, self._preprocess = open_clip.create_model_and_transforms(
                self._model_name, pretrained=self._pretrained, device=self._device
            )
            self._tokenizer = open_clip.get_tokenizer(self._model_name)
            self._model.eval()
            self._loaded_at = time.time()

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name=f"clip-{self._model_name.lower()}", version=self._pretrained,
            provider="local", loaded_at=self._loaded_at or 0, device=self._device,
        )

    def is_ready(self) -> bool:
        return self._model is not None

    def embed_text(self, text: str) -> np.ndarray:
        self.load()
        with torch.no_grad():
            tokens = self._tokenizer([text]).to(self._device)
            features = self._model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True)
        return features.cpu().numpy()[0]

    def embed_image_from_url(self, image_url: str) -> np.ndarray:
        self.load()
        resp = requests.get(image_url, timeout=10)
        image = Image.open(BytesIO(resp.content)).convert("RGB")
        return self._embed_image(image)

    def _embed_image(self, image: Image.Image) -> np.ndarray:
        self.load()
        with torch.no_grad():
            tensor = self._preprocess(image).unsqueeze(0).to(self._device)
            features = self._model.encode_image(tensor)
            features = features / features.norm(dim=-1, keepdim=True)
        return features.cpu().numpy()[0]

    def classify_image(self, image_url: str, candidate_labels: list[str]) -> list[dict]:
        self.load()
        image_features = self.embed_image_from_url(image_url)
        text_features = np.array([self.embed_text(label) for label in candidate_labels])
        similarities = image_features @ text_features.T
        return [
            {"label": label, "confidence": float(score)}
            for label, score in zip(candidate_labels, similarities[0])
        ]
