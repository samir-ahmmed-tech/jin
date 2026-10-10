import 'package:jin/models/chat_message.dart';

class AIResponse {
  final String content;
  final List<ToolCall>? toolCalls;

  AIResponse({required this.content, this.toolCalls});
}

abstract class BaseAIService {
  Future<AIResponse> sendMessage({
    required List<ChatMessage> messages,
    String? systemPrompt,
  });
}
