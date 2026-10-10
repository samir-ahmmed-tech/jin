import 'package:jin/models/ai_provider.dart';

class AppConstants {
  static const String appName = 'JIN';
  static const String appVersion = '1.0.0';
  static const String appTagline = 'Your AI Coding Super-Agent';
  
  static const String defaultSystemPrompt = '''
You are JIN, a powerful AI coding assistant. You help users with:
- Writing, debugging, and explaining code
- Creating projects and files
- Running terminal commands
- Answering technical questions

You are precise, helpful, and write clean code. When showing code, always use proper markdown code blocks with language identifiers.
''';

  static const List<AIModelConfig> availableModels = [
    // Gemini models
    AIModelConfig(id: 'gemini-2.5-pro-preview-06-05', displayName: 'Gemini 2.5 Pro', provider: AIProviderType.gemini, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'gemini-2.5-flash-preview-05-20', displayName: 'Gemini 2.5 Flash', provider: AIProviderType.gemini, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'gemini-2.0-flash', displayName: 'Gemini 2.0 Flash', provider: AIProviderType.gemini, supportsVision: true, supportsTools: true),
    // Claude models
    AIModelConfig(id: 'claude-sonnet-4-20250514', displayName: 'Claude Sonnet 4', provider: AIProviderType.claude, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'claude-3-7-sonnet-20250219', displayName: 'Claude 3.7 Sonnet', provider: AIProviderType.claude, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'claude-3-5-haiku-20241022', displayName: 'Claude 3.5 Haiku', provider: AIProviderType.claude, supportsVision: true, supportsTools: true),
    // DeepSeek models
    AIModelConfig(id: 'deepseek-chat', displayName: 'DeepSeek V3', provider: AIProviderType.deepseek, supportsTools: true),
    AIModelConfig(id: 'deepseek-reasoner', displayName: 'DeepSeek R1', provider: AIProviderType.deepseek),
    // OpenAI models
    AIModelConfig(id: 'gpt-4o', displayName: 'GPT-4o', provider: AIProviderType.openai, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'gpt-4o-mini', displayName: 'GPT-4o Mini', provider: AIProviderType.openai, supportsVision: true, supportsTools: true),
    AIModelConfig(id: 'o3-mini', displayName: 'o3-mini', provider: AIProviderType.openai, supportsTools: true),
  ];

  // API base URLs
  static const String geminiBaseUrl = 'https://generativelanguage.googleapis.com/v1beta';
  static const String claudeBaseUrl = 'https://api.anthropic.com/v1';
  static const String deepseekBaseUrl = 'https://api.deepseek.com/v1';
  static const String openaiBaseUrl = 'https://api.openai.com/v1';
  static const String openrouterBaseUrl = 'https://openrouter.ai/api/v1';
}
