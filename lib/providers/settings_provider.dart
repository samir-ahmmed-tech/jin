import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jin/models/ai_provider.dart';
import 'package:jin/services/storage_service.dart';
import 'package:jin/core/constants/app_constants.dart';

final settingsProvider = ChangeNotifierProvider<SettingsNotifier>((ref) {
  return SettingsNotifier();
});

class SettingsNotifier extends ChangeNotifier {
  String _selectedModelId = '';
  Map<AIProviderType, String> _apiKeys = {};
  bool _isLoading = true;

  String get selectedModelId => _selectedModelId;
  Map<AIProviderType, String> get apiKeys => _apiKeys;
  bool get isLoading => _isLoading;

  SettingsNotifier() {
    loadSettings();
  }

  Future<void> loadSettings() async {
    _isLoading = true;
    notifyListeners();

    _selectedModelId = await StorageService.instance.getSelectedModelId() ?? AppConstants.availableModels.first.id;
    
    for (var provider in AIProviderType.values) {
      final key = await StorageService.instance.getApiKey(provider);
      if (key != null && key.isNotEmpty) {
        _apiKeys[provider] = key;
      }
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> setApiKey(AIProviderType provider, String key) async {
    await StorageService.instance.saveApiKey(provider, key);
    _apiKeys[provider] = key;
    notifyListeners();
  }

  Future<void> removeApiKey(AIProviderType provider) async {
    await StorageService.instance.deleteApiKey(provider);
    _apiKeys.remove(provider);
    notifyListeners();
  }

  Future<void> setSelectedModel(String modelId) async {
    await StorageService.instance.saveSelectedModelId(modelId);
    _selectedModelId = modelId;
    notifyListeners();
  }

  String? getApiKey(AIProviderType provider) {
    return _apiKeys[provider];
  }

  bool hasApiKey(AIProviderType provider) {
    return _apiKeys.containsKey(provider) && _apiKeys[provider]!.isNotEmpty;
  }

  AIModelConfig? getSelectedModel() {
    try {
      return AppConstants.availableModels.firstWhere((model) => model.id == _selectedModelId);
    } catch (e) {
      return null;
    }
  }

  List<AIModelConfig> getAvailableModelsForProvider(AIProviderType provider) {
    return AppConstants.availableModels.where((model) => model.provider == provider).toList();
  }
}
