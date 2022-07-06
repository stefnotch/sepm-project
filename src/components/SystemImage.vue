<script setup lang="ts">
import { findBackendUrl } from '@/services/service';

defineProps<{ name: string }>();

const imagesBackendUrl = findBackendUrl('api/v1/images');
</script>
<template>
  <img :src="imagesBackendUrl + '/' + name" v-bind="$attrs" />
</template>
