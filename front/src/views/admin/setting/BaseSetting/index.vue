<template>
  <div class="app-container">
    <el-form :model="config" size="mini" label-width="200px" style="width: 80%">
      <el-form-item label="Torna根地址">
        <el-input
          v-model="config.tornaFrontUrl.value"
          @change="onConfigChange(config.tornaFrontUrl)"
        />
      </el-form-item>
      <el-form-item label="允许注册">
        <el-switch
          v-model="config.regEnable.value"
          active-text="允许"
          active-value="true"
          inactive-text=""
          inactive-value="false"
          @change="onConfigChange(config.regEnable)"
        />
      </el-form-item>
      <el-form-item label="文档排序规则">
        <el-radio-group v-model="config.docSortType.value" @change="onDocSortTypeChange">
          <el-radio-button label="by_order">根据排序字段排序</el-radio-button>
          <el-radio-button label="by_name">根据文档名称排序</el-radio-button>
          <el-radio-button label="by_url">根据URL排序</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <span slot="label">
          图片上传设置
          <el-link type="primary" :underline="false" class="el-icon-question" @click="$refs.help.open('static/help/upload.md')" />
        </span>
        <span>
          上传文件存放目录
        </span>
        <el-input
          v-model="config.uploadDir.value"
          placeholder="绝对路径，如：/opt/upload 不填默认在 {user.home}/torna_upload 下"
          @change="onConfigChange(config.uploadDir)"
        />

        <span>
          自定义域名
        </span>
        <el-input
          v-model="config.uploadDomain.value"
          placeholder="如：http://images.xxx.com、http://images.xxx.com/upload"
          @change="onConfigChange(config.uploadDomain)"
        />
      </el-form-item>
    </el-form>
    <help ref="help" />
  </div>
</template>

<script>
import Help from '@/components/Help'
import { saveAdminConfig, loadAdminConfig } from '../setting'

export default {
  name: 'BaseSetting',
  components: { Help },
  data() {
    return {
      config: {
        regEnable: { key: 'torna.register.enable', value: 'false' },
        docSortType: { key: 'torna.doc-sort-type', value: 'by_order', remark: '文档排序规则' },
        uploadDir: { key: 'torna.upload.dir', value: '', remark: '上传文件保存目录' },
        uploadDomain: { key: 'torna.upload.domain', value: '', remark: '上传文件映射' },
        tornaFrontUrl: { key: 'torna.front-url', value: '', remark: 'Torna前端地址' }
      },
      docSortTypeMap: {
        'by_order': '根据排序字段排序',
        'by_name': '根据文档名称排序',
        'by_url': '根据URL排序'
      }
    }
  },
  created() {
    this.reload()
  },
  methods: {
    reload() {
      loadAdminConfig(this.config, () => {
        const baseUrlConfig = this.config.tornaFrontUrl
        if (!baseUrlConfig.value) {
          baseUrlConfig.value = this.getBaseUrl()
          saveAdminConfig(baseUrlConfig, true)
        }
      })
    },
    onConfigChange(config) {
      saveAdminConfig(config)
    },
    onDocSortTypeChange(val) {
      this.config.docSortType.remark = '文档排序规则，' + this.docSortTypeMap[val]
      this.onConfigChange(this.config.docSortType)
    }
  }
}
</script>
