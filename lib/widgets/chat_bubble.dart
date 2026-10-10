import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:jin/models/chat_message.dart';
import 'package:jin/widgets/code_block.dart';
import 'package:jin/widgets/tool_result_card.dart';
import 'package:intl/intl.dart';

class ChatBubble extends StatelessWidget {
  final ChatMessage message;

  const ChatBubble({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    final isUser = message.role == MessageRole.user;
    final hasError = message.isError;
    final timeStr = DateFormat('HH:mm').format(message.timestamp);

    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: Row(
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!isUser) ...[
            const CircleAvatar(
              backgroundColor: Color(0xFF1A1A2E),
              radius: 16,
              child: Icon(Icons.smart_toy_rounded, size: 18, color: Color(0xFF4FC3F7)),
            ),
            const SizedBox(width: 8),
          ],
          
          Flexible(
            child: GestureDetector(
              onLongPress: () => _showOptions(context),
              child: Container(
                constraints: BoxConstraints(
                  maxWidth: MediaQuery.of(context).size.width * 0.8,
                ),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: hasError 
                      ? Colors.redAccent.withOpacity(0.2)
                      : isUser 
                          ? const Color(0xFF1565C0).withOpacity(0.8) 
                          : const Color(0xFF1A1A2E),
                  borderRadius: BorderRadius.only(
                    topLeft: const Radius.circular(16),
                    topRight: const Radius.circular(16),
                    bottomLeft: Radius.circular(isUser ? 16 : 4),
                    bottomRight: Radius.circular(isUser ? 4 : 16),
                  ),
                  border: hasError ? Border.all(color: Colors.redAccent.withOpacity(0.5)) : null,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (!isUser && message.modelId != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8.0),
                        child: Text(
                          message.modelId!,
                          style: const TextStyle(
                            fontSize: 10,
                            color: Color(0xFF4FC3F7),
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      
                    if (message.toolCalls != null && message.toolCalls!.isNotEmpty)
                      ...message.toolCalls!.map((tool) => Padding(
                        padding: const EdgeInsets.only(bottom: 8.0),
                        child: ToolResultCard(toolCall: tool),
                      )),
                      
                    if (message.content.isNotEmpty)
                      MarkdownBody(
                        data: message.content + (message.isStreaming ? ' ⚪' : ''),
                        styleSheet: MarkdownStyleSheet(
                          p: const TextStyle(color: Colors.white, fontSize: 15, height: 1.4),
                          code: const TextStyle(
                            backgroundColor: Color(0xFF0A0A14),
                            color: Color(0xFF4FC3F7),
                            fontFamily: 'monospace',
                          ),
                          codeblockDecoration: BoxDecoration(
                            color: const Color(0xFF0A0A14),
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        builders: {
                          'code': CodeBlockBuilder(),
                        },
                      ),
                      
                    const SizedBox(height: 6),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (hasError)
                          const Padding(
                            padding: EdgeInsets.only(right: 4.0),
                            child: Icon(Icons.error_outline, size: 12, color: Colors.redAccent),
                          ),
                        Text(
                          timeStr,
                          style: TextStyle(
                            fontSize: 10,
                            color: Colors.white.withOpacity(0.5),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
          
          if (isUser) ...[
            const SizedBox(width: 8),
            const CircleAvatar(
              backgroundColor: Color(0xFF1565C0),
              radius: 16,
              child: Icon(Icons.person, size: 18, color: Colors.white),
            ),
          ],
        ],
      ),
    );
  }

  void _showOptions(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.copy, color: Colors.white),
              title: const Text('Copy Text', style: TextStyle(color: Colors.white)),
              onTap: () {
                Clipboard.setData(ClipboardData(text: message.content));
                Navigator.pop(ctx);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Copied to clipboard')),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class CodeBlockBuilder extends MarkdownElementBuilder {
  @override
  Widget? visitElementAfter(mdElement, TextStyle? preferredStyle) {
    var language = '';
    if (mdElement.attributes['class'] != null) {
      String lg = mdElement.attributes['class'] as String;
      language = lg.substring(9); // remove 'language-'
    }
    return CodeBlock(
      code: mdElement.textContent,
      language: language.isNotEmpty ? language : null,
    );
  }
}
