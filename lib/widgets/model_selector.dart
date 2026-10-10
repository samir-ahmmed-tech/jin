import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/core/constants/app_constants.dart';
import 'package:jin/models/ai_provider.dart';
import 'package:jin/providers/settings_provider.dart';

class ModelSelector extends ConsumerWidget {
  const ModelSelector({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final settingsNotifier = ref.read(settingsProvider.notifier);
    final currentModel = settingsNotifier.getSelectedModel();
    final modelName = currentModel?.displayName ?? 'Select Model';

    return ActionChip(
      backgroundColor: const Color(0xFF151525),
      side: const BorderSide(color: Colors.white12),
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            modelName,
            style: const TextStyle(color: Colors.white, fontSize: 13),
          ),
          const SizedBox(width: 4),
          const Icon(Icons.keyboard_arrow_down, size: 16, color: Colors.white54),
        ],
      ),
      onPressed: () => _showModelBottomSheet(context, ref, settingsNotifier),
    );
  }

  void _showModelBottomSheet(BuildContext context, WidgetRef ref, dynamic settingsNotifier) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.6,
        minChildSize: 0.4,
        maxChildSize: 0.9,
        expand: false,
        builder: (_, scrollController) {
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white24,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const Text(
                'Select AI Model',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: ListView.builder(
                  controller: scrollController,
                  itemCount: AIProviderType.values.length,
                  itemBuilder: (context, index) {
                    final provider = AIProviderType.values[index];
                    final hasKey = settingsNotifier.hasApiKey(provider);
                    final availableModels = AppConstants.availableModels
                        .where((m) => m.provider == provider)
                        .toList();
                        
                    if (availableModels.isEmpty) return const SizedBox.shrink();

                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                          child: Row(
                            children: [
                              Text(provider.iconAsset, style: const TextStyle(fontSize: 18)),
                              const SizedBox(width: 8),
                              Text(
                                provider.displayName.toUpperCase(),
                                style: const TextStyle(
                                  color: Color(0xFF4FC3F7),
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                  letterSpacing: 1.2,
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (!hasKey)
                          const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                            child: Text(
                              'Add key in Settings',
                              style: TextStyle(color: Colors.white38, fontStyle: FontStyle.italic),
                            ),
                          )
                        else
                          ...availableModels.map((model) {
                            final isSelected = settingsNotifier.selectedModelId == model.id;
                            return ListTile(
                              contentPadding: const EdgeInsets.symmetric(horizontal: 24),
                              title: Text(model.displayName, style: const TextStyle(color: Colors.white)),
                              subtitle: model.description != null
                                  ? Text(model.description!,
                                      style: const TextStyle(color: Colors.white54, fontSize: 12))
                                  : null,
                              trailing: isSelected
                                  ? const Icon(Icons.check_circle, color: Color(0xFF4FC3F7))
                                  : null,
                              onTap: () {
                                settingsNotifier.setSelectedModel(model.id);
                                Navigator.pop(ctx);
                              },
                            );
                          }),
                        const Divider(color: Colors.white12, indent: 20, endIndent: 20),
                      ],
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
