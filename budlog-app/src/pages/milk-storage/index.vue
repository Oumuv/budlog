<script setup lang="ts">
import { Save, Trash2 } from "lucide-vue-next";
import { NButton, NInputNumber } from "naive-ui";
import { onBackPress, onLoad } from "@dcloudio/uni-app";
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import DateTimeField from "../../components/DateTimeField.vue";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import OptionalNoteField from "../../components/OptionalNoteField.vue";
import PageHeader from "../../components/PageHeader.vue";
import { nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const LAST_AMOUNT_KEY = "budlog.milkStorage.lastAmount";
const recordId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dirty = ref(false);
const lastAmount = ref<number>();
let hydrating = true;

const form = reactive({
  clientRequestId: uuid(),
  storedAt: nowLocalInput(),
  amountMl: "",
  note: "",
});

const amountPresets = [60, 90, 120];
const amountValue = computed<number | null>({
  get: () => form.amountMl === "" ? null : Number(form.amountMl),
  set: (value) => { form.amountMl = value === null ? "" : String(value); },
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const storedAmount = Number(uni.getStorageSync(LAST_AMOUNT_KEY));
  if (Number.isFinite(storedAmount) && storedAmount > 0 && storedAmount <= 1000) {
    lastAmount.value = storedAmount;
  }
  const id = Number(options?.id || 0);
  if (id) {
    recordId.value = id;
    await loadRecord(id);
  }
  await nextTick();
  hydrating = false;
  dirty.value = false;
});

onBackPress(() => {
  if (!dirty.value || saving.value) return false;
  uni.showModal({
    title: "放弃未保存内容",
    content: "当前修改尚未保存",
    success: (result) => {
      if (result.confirm) {
        dirty.value = false;
        uni.navigateBack();
      }
    },
  });
  return true;
});

async function loadRecord(id: number) {
  loading.value = true;
  try {
    const record = await api.milkStorage(id);
    form.clientRequestId = record.clientRequestId;
    form.storedAt = toLocalInput(record.storedAt);
    form.amountMl = String(record.amountMl);
    form.note = record.note || "";
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

function validate(): string | undefined {
  const amount = Number(form.amountMl);
  if (!form.amountMl || !Number.isFinite(amount) || amount < 0.1 || amount > 1000) {
    return "请输入 0.1 至 1000 ml 的存奶量";
  }
  if (Math.abs(amount * 10 - Math.round(amount * 10)) > 1e-9) {
    return "存奶量最多保留 1 位小数";
  }
  return undefined;
}

async function save() {
  if (saving.value) return;
  const message = validate();
  if (message) {
    uni.showToast({ title: message, icon: "none" });
    return;
  }
  saving.value = true;
  try {
    const amount = Number(form.amountMl);
    const payload = {
      clientRequestId: form.clientRequestId,
      storedAt: toIso(form.storedAt),
      amountMl: amount,
      note: form.note.trim() || undefined,
    };
    if (recordId.value) await api.updateMilkStorage(recordId.value, payload);
    else await api.createMilkStorage(payload);
    lastAmount.value = amount;
    uni.setStorageSync(LAST_AMOUNT_KEY, amount);
    dirty.value = false;
    uni.showToast({ title: "已保存", icon: "success" });
    setTimeout(() => uni.navigateBack(), 350);
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  } finally {
    saving.value = false;
  }
}

function remove() {
  if (!recordId.value || deleting.value) return;
  uni.showModal({
    title: "删除存奶记录",
    content: "删除后今日存奶统计会立即重算",
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !recordId.value) return;
      deleting.value = true;
      try {
        await api.deleteMilkStorage(recordId.value);
        dirty.value = false;
        uni.navigateBack();
      } catch (exception) {
        uni.showToast({ title: exception instanceof Error ? exception.message : "删除失败", icon: "none" });
      } finally {
        deleting.value = false;
      }
    },
  });
}

function setAmount(amount: number) {
  form.amountMl = String(amount);
}
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form milk-storage-page">
    <PageHeader :title="recordId ? '编辑存奶记录' : '记录存奶'" back />

    <AppLoading v-if="loading" copy="正在加载存奶记录" />

    <view v-else class="milk-storage-form surface">
      <view class="field">
        <text class="field__label">存奶时间</text>
        <DateTimeField v-model="form.storedAt" title="选择存奶时间" quick-record :max-now-offset-minutes="5" />
      </view>

      <view class="field">
        <text class="field__label">存奶量（ml）</text>
        <view class="amount-stepper">
          <NInputNumber
            v-model:value="amountValue"
            :min="0.1"
            :max="1000"
            :step="10"
            :precision="1"
            button-placement="both"
            placeholder="0"
          />
          <text class="amount-stepper__unit">ml</text>
        </view>
        <button
          v-if="lastAmount"
          class="last-amount-action"
          :class="{ 'last-amount-action--active': Number(form.amountMl) === lastAmount }"
          :aria-pressed="Number(form.amountMl) === lastAmount"
          hover-class="none"
          @click="setAmount(lastAmount)"
        >
          沿用上次 {{ lastAmount }} ml
        </button>
        <view v-else class="amount-presets shortcut-row">
          <button
            v-for="amount in amountPresets"
            :key="amount"
            class="shortcut-chip amount-preset"
            :class="{ 'shortcut-chip--active': Number(form.amountMl) === amount }"
            @click="setAmount(amount)"
          >
            {{ amount }}
          </button>
        </view>
      </view>

      <OptionalNoteField v-model="form.note" />

      <view class="form-actions">
        <NButton v-if="recordId" type="error" size="large" secondary block :loading="deleting" @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : recordId ? "保存修改" : "记录存奶" }}</NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.milk-storage-form {
  padding: 16px;
}

.amount-presets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  margin-top: 8px;
}

.amount-preset {
  width: 100%;
  min-width: 0;
  padding-right: 8px;
  padding-left: 8px;
}

.last-amount-action {
  box-sizing: border-box;
  width: 100%;
  min-height: 46px;
  margin: 8px 0 0;
  padding: 0 12px;
  border: 1px solid #cfe1fb;
  border-radius: 8px;
  color: #246bb8;
  background: #f2f7ff;
  font-size: 14px;
  font-weight: 750;
}

.last-amount-action--active {
  border-color: #8fb9ef;
  background: #e8f2ff;
}

.amount-stepper {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  background: #fffdfd;
}

.amount-stepper__unit {
  color: var(--bud-color-muted);
  font-size: 14px;
  font-weight: 650;
}
</style>
