<script setup lang="ts">
import { ChevronDown, ChevronUp, MessageSquarePlus } from "lucide-vue-next";
import { NInput } from "naive-ui";
import { ref, watch } from "vue";

const props = withDefaults(defineProps<{
  modelValue: string;
  maxLength?: number;
  placeholder?: string;
  quickOptions?: string[];
}>(), {
  maxLength: 500,
  placeholder: "可选",
  quickOptions: () => [],
});
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const expanded = ref(false);

watch(() => props.modelValue, (value) => {
  if (value) expanded.value = true;
}, { immediate: true });

function appendQuickOption(option: string) {
  const current = props.modelValue.trimEnd();
  const separator = current && !/[，,]\s*$/.test(current) ? "," : "";
  const nextValue = `${current}${separator}${option}`;
  if (nextValue.length > props.maxLength) {
    uni.showToast({ title: "备注已达字数上限", icon: "none" });
    return;
  }
  emit("update:modelValue", nextValue);
}
</script>

<template>
  <view class="optional-note">
    <button
      class="optional-note__toggle"
      :aria-expanded="expanded"
      hover-class="none"
      @click="expanded = !expanded"
    >
      <MessageSquarePlus :size="19" />
      <text>{{ expanded ? "备注（可选）" : "添加备注" }}</text>
      <ChevronUp v-if="expanded" :size="19" />
      <ChevronDown v-else :size="19" />
    </button>
    <view v-if="expanded" class="optional-note__editor">
      <NInput
        :value="modelValue"
        type="textarea"
        :maxlength="maxLength"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="placeholder"
        @update:value="emit('update:modelValue', $event)"
      />
      <view v-if="quickOptions.length" class="optional-note__quick">
        <text class="optional-note__quick-title">常用词</text>
        <view class="optional-note__quick-options" aria-label="常用备注词">
          <button
            v-for="option in quickOptions"
            :key="option"
            class="shortcut-chip"
            :aria-label="`追加${option}到备注`"
            hover-class="none"
            @click="appendQuickOption(option)"
          >
            {{ option }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.optional-note {
  margin-bottom: 20px;
}

.optional-note__toggle {
  box-sizing: border-box;
  display: grid;
  width: 100%;
  min-height: 50px;
  appearance: none;
  margin: 0;
  padding: 0 12px;
  grid-template-columns: 22px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--bud-color-line-soft);
  border-radius: 8px;
  color: var(--bud-color-body);
  background: var(--bud-color-surface);
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  touch-action: manipulation;
}

.optional-note__toggle > .lucide:first-child {
  color: var(--bud-color-primary);
}

.optional-note__toggle > .lucide:last-child {
  color: var(--bud-color-muted);
}

.optional-note__toggle:active {
  border-color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.optional-note__editor {
  margin-top: 8px;
}

.optional-note__quick {
  margin-top: 10px;
}

.optional-note__quick-title {
  display: block;
  margin-bottom: 7px;
  color: var(--bud-color-muted);
  font-size: 11px;
  font-weight: 700;
}

.optional-note__quick-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.optional-note__quick-options .shortcut-chip {
  min-height: 32px;
  padding: 0 11px;
  font-size: 12px;
}
</style>
