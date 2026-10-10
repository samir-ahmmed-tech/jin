import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/models/ai_provider.dart';
import 'package:jin/services/ai/base_ai_service.dart';
import 'package:jin/services/ai/claude_service.dart';
import 'package:jin/services/ai/deepseek_service.dart';
import 'package:jin/services/ai/gemini_service.dart';
import 'package:jin/services/ai/openai_service.dart';
import 'package:jin/services/ai/openrouter_service.dart';
import 'package:jin/providers/settings_provider.dart';

final aiServiceProvider = Provider<BaseAIService?>((ref) {
  final settings = ref.watch(settingsProvider);
  final selectedModel = settings.getSelectedModel();

  if (selectedModel == null) return null;

  final providerType = selectedModel.provider;
  final apiKey = settings.getApiKey(providerType);

  if (apiKey == null || apiKey.isEmpty) return null;

  switch (providerType) {
    case AIProviderType.gemini:
      return GeminiService(apiKey: apiKey, modelId: selectedModel.id);
    case AIProviderType.claude:
      return ClaudeService(apiKey: apiKey, modelId: selectedModel.id);
    case AIProviderType.deepseek:
      return DeepSeekService(apiKey: apiKey, modelId: selectedModel.id);
    case AIProviderType.openai:
      return OpenAIService(apiKey: apiKey, modelId: selectedModel.id);
    case AIProviderType.openrouter:
      return OpenRouterService(apiKey: apiKey, modelId: selectedModel.id);
  }
});
