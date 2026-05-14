from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional
import time

@dataclass
class ModelInfo:
    name: str
    version: str
    provider: str
    loaded_at: float
    device: str

class BaseAiModel(ABC):
    @property
    @abstractmethod
    def model_info(self) -> ModelInfo:
        pass

    @abstractmethod
    def is_ready(self) -> bool:
        pass

    def health_check(self) -> dict:
        return {
            "model": self.model_info.name,
            "version": self.model_info.version,
            "provider": self.model_info.provider,
            "device": self.model_info.device,
            "ready": self.is_ready()
        }
