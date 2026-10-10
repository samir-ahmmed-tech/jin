import 'package:dio/dio.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/services/ai/base_ai_service.dart';

class DeepSeekService implements BaseAIService {
  final String apiKey;
  final String modelId;
  final Dio _dio;

  DeepSeekService({required this.apiKey, required this.modelId})
      : _dio = Dio(BaseOptions(
          baseUrl: 'https://api.deepseek.com/v1',
          headers: {
            'Authorization': 'Bearer $apiKey',
            'Content-Type': 'application/json',
          },
        ));

  @override
  Future<AIResponse> sendMessage({
    required List<ChatMessage> messages,
    String? systemPrompt,
  }) async {
    try {
      final List<Map<String, dynamic>> formattedMessages = [];
      
      if (systemPrompt != null && systemPrompt.isNotEmpty) {
        formattedMessages.add({
          'role': 'system',
          'content': systemPrompt,
        });
      }

      formattedMessages.addAll(messages.map((m) {
        String role = 'user';
        if (m.role == MessageRole.assistant) role = 'assistant';
        if (m.role == MessageRole.system) role = 'system';
        
        return {
          'role': role,
          'content': m.content
        };
      }));

      final Map<String, dynamic> data = {
        'model': modelId,
        'messages': formattedMessages,
        'max_tokens': 4096,
      };

      final response = await _dio.post('/chat/completions', data: data);

      if (response.statusCode == 200) {
        final choices = response.data['choices'] as List?;
        if (choices != null && choices.isNotEmpty) {
          final message = choices[0]['message'];
          if (message != null) {
            final content = message['content'] as String?;
            if (content != null) {
              return AIResponse(content: content);
            }
          }
        }
        throw Exception('Invalid response format from DeepSeek');
      } else {
        throw Exception('DeepSeek API Error: ${response.statusCode}');
      }
    } on DioException catch (e) {
      return AIResponse(
          content: 'Error: Failed to communicate with DeepSeek API. ${e.message}');
    } catch (e) {
      return AIResponse(content: 'Error: An unexpected error occurred. $e');
    }
  }
}
