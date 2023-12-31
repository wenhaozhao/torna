<template>
  <div>
    <el-button
      type="primary"
      size="mini"
      style="margin-bottom: 10px"
      @click="onHeaderAdd"
    >
      {{ $t('add') }}
    </el-button>
    <el-table
      :data="globalHeaders"
      border
      highlight-current-row
    >
      <el-table-column label="Name" prop="name" width="300px" />
      <el-table-column label="Value" prop="example" />
      <el-table-column
        prop="required"
        :label="$t('require')"
        :width="$width(60, { 'en': 75 })"
      >
        <template slot-scope="scope">
          <span :class="scope.row.required ? 'danger' : ''">{{ scope.row.required ? $t('yes') : $t('no') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('description')" prop="description" />
      <el-table-column
        :label="$t('operation')"
        width="150"
      >
        <template slot-scope="scope">
          <el-link type="primary" size="mini" @click="onHeaderUpdate(scope.row)">{{ $t('update') }}</el-link>
          <el-popconfirm
            :title="$t('deleteConfirm', scope.row.name)"
            @confirm="onHeaderDelete(scope.row)"
          >
            <el-link slot="reference" type="danger" size="mini">{{ $t('delete') }}</el-link>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <!--dialog-->
    <el-dialog
      :title="dialogHeaderTitle"
      :visible.sync="dialogHeaderVisible"
      :close-on-click-modal="false"
      @close="resetForm('dialogHeaderForm')"
    >
      <el-form
        ref="dialogHeaderForm"
        :rules="dialogHeaderFormRules"
        :model="dialogHeaderFormData"
        label-width="120px"
        size="mini"
      >
        <el-form-item
          prop="name"
          label="Name"
        >
          <el-input v-model="dialogHeaderFormData.name" placeholder="name" show-word-limit maxlength="50" />
        </el-form-item>
        <el-form-item
          prop="example"
          label="Value"
        >
          <el-input v-model="dialogHeaderFormData.example" placeholder="value" clearable />
        </el-form-item>
        <el-form-item
          prop="required"
          :label="$t('required')"
        >
          <el-switch
            v-model="dialogHeaderFormData.required"
            :active-value="1"
            :inactive-value="0"
            :active-text="$t('yes')"
            :inactive-text="$t('no')"
          />
        </el-form-item>
        <el-form-item
          prop="description"
          :label="$t('description')"
        >
          <el-input v-model="dialogHeaderFormData.description" type="textarea" :placeholder="$t('description')" show-word-limit maxlength="200" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogHeaderVisible = false">{{ $t('dlgCancel') }}</el-button>
        <el-button type="primary" @click="onDialogHeaderSave">{{ $t('dlgSave') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
export default {
  data() {
    return {
      globalHeaders: [],
      environmentId: '',
      dialogHeaderVisible: false,
      dialogHeaderTitle: '',
      dialogHeaderFormData: {
        id: '',
        environmentId: '',
        name: '',
        example: '',
        required: 1,
        description: ''
      },
      dialogHeaderFormRules: {
        name: [
          { required: true, message: this.$t('notEmpty'), trigger: 'blur' },
          { validator: (rule, value, callback) => {
            if (value && !/^[a-zA-Z0-9\-_]+$/.test(value)) {
              callback(new Error(this.$t('errorParam')))
            } else {
              callback()
            }
          }, trigger: 'blur' }
        ], example: [
          { required: true, message: this.$t('notEmpty'), trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    reload(environmentId) {
      if (environmentId) {
        this.environmentId = environmentId
      }
      this.loadHeaders(this.environmentId)
    },
    loadHeaders(environmentId) {
      this.get('module/environment/param/list', {
        environmentId: environmentId,
        style: this.getEnums().PARAM_STYLE.header
      }, resp => {
        this.globalHeaders = resp.data
      })
    },
    onHeaderAdd() {
      this.dialogHeaderTitle = this.$t('newHeader')
      this.dialogHeaderVisible = true
      this.dialogHeaderFormData.id = ''
    },
    onHeaderUpdate(row) {
      this.dialogHeaderTitle = this.$t('updateHeader')
      this.dialogHeaderVisible = true
      this.$nextTick(() => {
        Object.assign(this.dialogHeaderFormData, row)
      })
    },
    onHeaderDelete(row) {
      this.post('module/environment/param/delete', row, () => {
        this.tipSuccess(this.$t('deleteSuccess'))
        this.reload()
      })
    },
    onDialogHeaderSave() {
      this.$refs.dialogHeaderForm.validate((valid) => {
        if (valid) {
          const uri = this.dialogHeaderFormData.id ? 'module/environment/param/update' : 'module/environment/param/add'
          this.dialogHeaderFormData.environmentId = this.environmentId
          this.dialogHeaderFormData.style = this.getEnums().PARAM_STYLE.header
          this.post(uri, this.dialogHeaderFormData, () => {
            this.dialogHeaderVisible = false
            this.reload()
          })
        }
      })
    }
  }
}
</script>
