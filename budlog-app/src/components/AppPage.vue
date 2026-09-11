<script setup lang="ts">
import { dateZhCN, NConfigProvider, zhCN } from "naive-ui";
import { themeOverrides } from "../design";
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN" abstract>
    <slot />
  </NConfigProvider>
</template>
