import 'package:flutter/material.dart';
import 'package:jin/models/chat_message.dart';

class ToolResultCard extends StatefulWidget {
  final ToolCall toolCall;

  const ToolResultCard({super.key, required this.toolCall});

  @override
  State<ToolResultCard> createState() => _ToolResultCardState();
}

class _ToolResultCardState extends State<ToolResultCard> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    final hasResult = widget.toolCall.result != null;
    final isError = widget.toolCall.hasError;

    return Card(
      color: const Color(0xFF151525),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(
          color: isError ? Colors.redAccent.withOpacity(0.5) : Colors.white12,
        ),
      ),
      margin: EdgeInsets.zero,
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: _isExpanded,
          onExpansionChanged: (val) => setState(() => _isExpanded = val),
          tilePadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
          leading: Icon(
            Icons.build_circle_outlined,
            color: isError ? Colors.redAccent : const Color(0xFF4FC3F7),
            size: 20,
          ),
          title: Text(
            'Used tool: ${widget.toolCall.name}',
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
          trailing: hasResult
              ? Icon(
                  _isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                  color: Colors.white54,
                  size: 20,
                )
              : const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF4FC3F7)),
                ),
          children: [
            if (hasResult)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: const BoxDecoration(
                  color: Color(0xFF0A0A14),
                  borderRadius: BorderRadius.vertical(bottom: Radius.circular(8)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Input:',
                      style: TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.toolCall.arguments.toString(),
                      style: const TextStyle(color: Colors.white70, fontFamily: 'monospace', fontSize: 12),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      isError ? 'Error:' : 'Result:',
                      style: TextStyle(
                        color: isError ? Colors.redAccent : Colors.white54,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.toolCall.result.toString(),
                      style: const TextStyle(color: Colors.white70, fontFamily: 'monospace', fontSize: 12),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
