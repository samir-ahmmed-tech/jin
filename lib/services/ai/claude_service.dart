import 'package:dio/dio.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/services/ai/base_ai_service.dart';

class ClaudeService implements BaseAIService {
  final String apiKey;
  final String modelId;
  final Dio _dio;

  ClaudeService({required this.apiKey, required this.modelId})
      : _dio = Dio(BaseOptions(
          baseUrl: 'https://api.anthropic.com/v1',
          headers: {
            'x-api-key': apiKey,
            'anthropic-version': '2023-06-01',
            'content-type': 'application/json',
          },
        ));

  @override
  Future<AIResponse> sendMessage({
    required List<ChatMessage> messages,
    String? systemPrompt,
  }) async {
    try {
      final List<Map<String, dynamic>> formattedMessages = messages
          .where((m) => m.role != MessageRole.system)
          .map((m) {
        return {
          'role': m.role == MessageRole.user ? 'user' : 'assistant',
          'content': m.content
        };
      }).toList();

      final Map<String, dynamic> data = {
        'model': modelId,
        'max_tokens': 4096,
        'messages': formattedMessages,
      };

      if (systemPrompt != null && systemPrompt.isNotEmpty) {
        data['system'] = systemPrompt;
      }

      final response = await _dio.post('/messages', data: data);

      if (response.statusCode == 200) {
        final contentList = response.data['content'] as List?;
        if (contentList != null && contentList.isNotEmpty) {
          final text = contentList[0]['text'] as String?;
          if (text != null) {
            return AIResponse(content: text);
          }
        }
        throw Exception('Invalid response format from Claude');
      } else {
        throw Exception('Claude API Error: ${response.statusCode}');
      }
    } on DioException catch (e) {
      return AIResponse(
          content: 'Error: Failed to communicate with Claude API. ${e.message}');
    } catch (e) {
      return AIResponse(content: 'Error: An unexpected error occurred. $e');
    }
  }
}
