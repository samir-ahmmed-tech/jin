enum AIProviderType { gemini, claude, deepseek, openai, openrouter }

class AIModelConfig {
  final String id;
  final String displayName;
  final AIProviderType provider;
  final String? description;
  final bool supportsVision;
  final bool supportsTools;

  const AIModelConfig({
    required this.id,
    required this.displayName,
    required this.provider,
    this.description,
    this.supportsVision = false,
    this.supportsTools = false,
  });
}

extension AIProviderTypeExtension on AIProviderType {
  String get displayName {
    switch (this) {
      case AIProviderType.gemini: return 'Google Gemini';
      case AIProviderType.claude: return 'Anthropic Claude';
      case AIProviderType.deepseek: return 'DeepSeek';
      case AIProviderType.openai: return 'OpenAI';
      case AIProviderType.openrouter: return 'OpenRouter';
    }
  }

  String get iconAsset {
    switch (this) {
      case AIProviderType.gemini: return '✦';
      case AIProviderType.claude: return '◉';
      case AIProviderType.deepseek: return '◈';
      case AIProviderType.openai: return '◎';
      case AIProviderType.openrouter: return '⊕';
    }
  }
}
