package com.droid.hdhrmv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.hdhrmv.data.HdHomeRunParser
import com.droid.hdhrmv.data.HdHomeRunRepository
import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.model.SlotState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MultiViewUiState(
    val isLoading: Boolean = false,
    val isDiscovering: Boolean = false,
    val devices: List<HdHomeRunDevice> = emptyList(),
    val selectedDevice: HdHomeRunDevice? = null,
    val channels: List<Channel> = emptyList(),
    val freeTunerCount: Int = 0,
    val totalTunerCount: Int = 0,
    val slots: List<SlotState> = MultiviewLayoutManager.createInitialSlots(),
    val focusedSlotIndex: Int = 0,
    val multiviewMode: MultiviewMode = MultiviewMode.GRID_4,
    val isChannelPickerOpen: Boolean = false,
    val channelPickerTargetSlot: Int? = null,
    val slotActionTargetSlot: Int? = null,
    val isQuickStartOpen: Boolean = false,
    val hasShownStartupQuickStart: Boolean = false,
    val isTopBarFocused: Boolean = false,
    val errorMessage: String? = null
)

class MultiViewViewModel(
    private val repository: HdHomeRunRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiViewUiState())
    val uiState: StateFlow<MultiViewUiState> = _uiState.asStateFlow()

    init {
        discoverDevices()
    }

    fun discoverDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, errorMessage = null) }
            try {
                val devices = repository.discoverDevices()
                _uiState.update { current ->
                    current.copy(
                        isDiscovering = false,
                        devices = devices,
                        selectedDevice = current.selectedDevice ?: devices.firstOrNull()
                    )
                }
                val currentDevice = _uiState.value.selectedDevice
                if (currentDevice != null) {
                    selectDevice(currentDevice)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        errorMessage = "Discovery failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectDevice(device: HdHomeRunDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedDevice = device, errorMessage = null) }
            try {
                val lineup = repository.fetchLineup(device.lineupUrl)
                val tuners = repository.fetchTunerStatus(device.baseUrl)
                val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, device.tunerCount)

                _uiState.update { current ->
                    val shouldShowQuickStart = !current.hasShownStartupQuickStart &&
                        current.slots.all { it.channel == null } &&
                        lineup.isNotEmpty()

                    current.copy(
                        isLoading = false,
                        selectedDevice = device.copy(freeTunerCount = freeCount),
                        channels = lineup,
                        freeTunerCount = freeCount,
                        totalTunerCount = device.tunerCount,
                        isQuickStartOpen = if (shouldShowQuickStart) true else current.isQuickStartOpen,
                        hasShownStartupQuickStart = if (shouldShowQuickStart) true else current.hasShownStartupQuickStart
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed loading device data: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshTunerStatus() {
        val device = _uiState.value.selectedDevice ?: return
        viewModelScope.launch {
            try {
                val tuners = repository.fetchTunerStatus(device.baseUrl)
                val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, device.tunerCount)
                _uiState.update {
                    it.copy(
                        freeTunerCount = freeCount,
                        totalTunerCount = device.tunerCount
                    )
                }
            } catch (_: Exception) {
                // Ignore transient tuner poll error
            }
        }
    }

    fun connectToManualIp(ip: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val device = repository.fetchDevice(ip)
                selectDevice(device)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to connect to $ip: ${e.message}"
                    )
                }
            }
        }
    }

    fun setChannelForSlot(slotIndex: Int, channel: Channel?) {
        _uiState.update { current ->
            val updatedSlots = MultiviewLayoutManager.assignChannel(current.slots, slotIndex, channel)
            current.copy(
                slots = updatedSlots,
                isChannelPickerOpen = false,
                channelPickerTargetSlot = null
            )
        }
    }

    fun setFocusedSlot(slotIndex: Int) {
        _uiState.update { current ->
            val updatedSlots = MultiviewLayoutManager.setFocusedSlot(current.slots, slotIndex)
            current.copy(
                focusedSlotIndex = slotIndex,
                slots = updatedSlots,
                isTopBarFocused = false
            )
        }
    }

    fun setTopBarFocused(focused: Boolean) {
        _uiState.update { it.copy(isTopBarFocused = focused) }
    }

    fun toggleSlotMute(slotIndex: Int) {
        _uiState.update { current ->
            val updatedSlots = MultiviewLayoutManager.toggleMute(current.slots, slotIndex)
            current.copy(slots = updatedSlots)
        }
    }

    fun setMultiviewMode(mode: MultiviewMode) {
        _uiState.update { it.copy(multiviewMode = mode, isTopBarFocused = false) }
    }

    fun openChannelPicker(slotIndex: Int) {
        _uiState.update {
            it.copy(
                isChannelPickerOpen = true,
                channelPickerTargetSlot = slotIndex,
                isTopBarFocused = false
            )
        }
        val device = _uiState.value.selectedDevice
        if (device != null && _uiState.value.channels.isEmpty()) {
            selectDevice(device)
        }
    }

    fun closeChannelPicker() {
        _uiState.update {
            it.copy(
                isChannelPickerOpen = false,
                channelPickerTargetSlot = null
            )
        }
    }

    fun clearSlot(slotIndex: Int) {
        setChannelForSlot(slotIndex, null)
    }

    fun nextChannel(slotIndex: Int) {
        val channelList = _uiState.value.channels
        if (channelList.isEmpty()) return
        val currentSlot = _uiState.value.slots.getOrNull(slotIndex)
        val currentIndex = channelList.indexOfFirst { it.guideNumber == currentSlot?.channel?.guideNumber }
        val nextIndex = if (currentIndex < 0 || currentIndex >= channelList.size - 1) 0 else currentIndex + 1
        setChannelForSlot(slotIndex, channelList[nextIndex])
    }

    fun previousChannel(slotIndex: Int) {
        val channelList = _uiState.value.channels
        if (channelList.isEmpty()) return
        val currentSlot = _uiState.value.slots.getOrNull(slotIndex)
        val currentIndex = channelList.indexOfFirst { it.guideNumber == currentSlot?.channel?.guideNumber }
        val prevIndex = if (currentIndex <= 0) channelList.size - 1 else currentIndex - 1
        setChannelForSlot(slotIndex, channelList[prevIndex])
    }

    fun cycleLayoutMode() {
        val modes = MultiviewMode.values()
        val currentMode = _uiState.value.multiviewMode
        val nextIndex = (modes.indexOf(currentMode) + 1) % modes.size
        setMultiviewMode(modes[nextIndex])
    }

    fun openSlotActions(slotIndex: Int) {
        _uiState.update { it.copy(slotActionTargetSlot = slotIndex, isTopBarFocused = false) }
    }

    fun closeSlotActions() {
        _uiState.update { it.copy(slotActionTargetSlot = null) }
    }

    fun selectNextSlot() {
        val total = MultiviewLayoutManager.MAX_SLOTS
        val current = _uiState.value.focusedSlotIndex
        val next = (current + 1) % total
        setFocusedSlot(next)
    }

    fun selectPreviousSlot() {
        val total = MultiviewLayoutManager.MAX_SLOTS
        val current = _uiState.value.focusedSlotIndex
        val prev = if (current - 1 < 0) total - 1 else current - 1
        setFocusedSlot(prev)
    }

    fun openQuickStart() {
        _uiState.update { it.copy(isQuickStartOpen = true, isTopBarFocused = false) }
    }

    fun closeQuickStart() {
        _uiState.update { it.copy(isQuickStartOpen = false) }
    }

    fun launchQuickStartChannels(selectedChannels: List<Channel>) {
        _uiState.update { current ->
            var updatedSlots = current.slots
            for (i in 0 until MultiviewLayoutManager.MAX_SLOTS) {
                val ch = selectedChannels.getOrNull(i)
                updatedSlots = MultiviewLayoutManager.assignChannel(updatedSlots, i, ch)
            }
            updatedSlots = MultiviewLayoutManager.setFocusedSlot(updatedSlots, 0)
            current.copy(
                slots = updatedSlots,
                focusedSlotIndex = 0,
                isQuickStartOpen = false,
                hasShownStartupQuickStart = true,
                multiviewMode = MultiviewMode.GRID_4,
                isTopBarFocused = false
            )
        }
    }
}
