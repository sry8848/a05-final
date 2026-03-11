<template>
  <div class="custom-select" :class="{ open: isOpen }" v-click-outside="closeDropdown">
    <div class="select-trigger" @click="toggleDropdown">
      <span class="select-value">{{ selectedLabel }}</span>
      <i class="fas fa-chevron-down select-arrow" :class="{ rotated: isOpen }"></i>
    </div>
    <transition name="dropdown">
      <div v-if="isOpen" class="select-dropdown">
        <div 
          v-for="option in options" 
          :key="option.value"
          class="select-option"
          :class="{ selected: modelValue === option.value }"
          @click="selectOption(option)"
        >
          <span class="option-text">{{ option.label }}</span>
          <i v-if="modelValue === option.value" class="fas fa-check option-check"></i>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'CustomSelect',
  props: {
    modelValue: {
      type: [String, Number],
      default: ''
    },
    options: {
      type: Array,
      default: () => []
    },
    placeholder: {
      type: String,
      default: '请选择'
    }
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const isOpen = ref(false)

    const selectedLabel = computed(() => {
      const option = props.options.find(opt => opt.value === props.modelValue)
      return option ? option.label : props.placeholder
    })

    const toggleDropdown = () => {
      isOpen.value = !isOpen.value
    }

    const closeDropdown = () => {
      isOpen.value = false
    }

    const selectOption = (option) => {
      emit('update:modelValue', option.value)
      emit('change', option.value)
      closeDropdown()
    }

    return {
      isOpen,
      selectedLabel,
      toggleDropdown,
      closeDropdown,
      selectOption
    }
  },
  directives: {
    'click-outside': {
      mounted(el, binding) {
        el._clickOutside = (event) => {
          if (!(el === event.target || el.contains(event.target))) {
            binding.value()
          }
        }
        document.addEventListener('click', el._clickOutside)
      },
      unmounted(el) {
        document.removeEventListener('click', el._clickOutside)
      }
    }
  }
}
</script>

<style scoped>
.custom-select {
  position: relative;
  min-width: 150px;
  font-family: inherit;
}

.select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.08));
  border: 1px solid rgba(59, 89, 152, 0.3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.custom-select:hover .select-trigger {
  border-color: rgba(59, 89, 152, 0.5);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.12));
  box-shadow: 0 4px 12px rgba(59, 89, 152, 0.2);
}

.custom-select.open .select-trigger {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 89, 152, 0.25), 0 4px 12px rgba(59, 89, 152, 0.25);
}

.select-value {
  font-size: 14px;
  color: #1a1a2e;
  font-weight: 600;
}

.select-arrow {
  font-size: 12px;
  color: #a8b4ff;
  transition: transform 0.3s ease;
}

.select-arrow.rotated {
  transform: rotate(180deg);
}

.select-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: #ffffff !important;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(59, 89, 152, 0.3);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  z-index: 1000;
  padding: 8px;
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s ease;
}

.select-option:hover {
  background: rgba(59, 89, 152, 0.1);
}

.select-option.selected {
  background: linear-gradient(135deg, rgba(59, 89, 152, 0.15), rgba(102, 126, 234, 0.1));
}

.option-text {
  font-size: 14px;
  color: #1a1a2e !important;
  font-weight: 500;
}

.select-option.selected .option-text {
  color: #3b5998 !important;
  font-weight: 600;
}

.option-check {
  font-size: 12px;
  color: #3b5998 !important;
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.25s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.dropdown-enter-to,
.dropdown-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
