import 'package:dio/dio.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/services/ai/base_ai_service.dart';

class GeminiService implements BaseAIService {
  final String apiKey;
  final String modelId;
  final Dio _dio;

  GeminiService({required this.apiKey, required this.modelId})
      : _dio = Dio(BaseOptions(
          baseUrl: 'https://generativelanguage.googleapis.com/v1beta/models',
        ));

  @override
  Future<AIResponse> sendMessage({
    required List<ChatMessage> messages,
    String? systemPrompt,
  }) async {
    try {
      final List<Map<String, dynamic>> contents = messages.map((m) {
        String role;
        if (m.role == MessageRole.user || m.role == MessageRole.system) {
          role = 'user';
        } else {
          role = 'model';
        }

        return {
          'role': role,
          'parts': [{'text': m.content}]
        };
      }).toList();

      final Map<String, dynamic> data = {
        'contents': contents,
      };

      if (systemPrompt != null && systemPrompt.isNotEmpty) {
        data['systemInstruction'] = {
          'parts': [{'text': systemPrompt}]
        };
      }

      final response = await _dio.post(
        '/$modelId:generateContent',
        queryParameters: {'key': apiKey},
        data: data,
      );

      if (response.statusCode == 200) {
        final candidates = response.data['candidates'] as List?;
        if (candidates != null && candidates.isNotEmpty) {
          final content = candidates[0]['content'];
          if (content != null) {
            final parts = content['parts'] as List?;
            if (parts != null && parts.isNotEmpty) {
              final text = parts[0]['text'] as String?;
              if (text != null) {
                return AIResponse(content: text);
              }
            }
          }
        }
        throw Exception('Invalid response format from Gemini');
      } else {
        throw Exception('Gemini API Error: ${response.statusCode}');
      }
    } on DioException catch (e) {
      return AIResponse(
          content: 'Error: Failed to communicate with Gemini API. ${e.message}');
    } catch (e) {
      return AIResponse(content: 'Error: An unexpected error occurred. $e');
    }
  }
}
