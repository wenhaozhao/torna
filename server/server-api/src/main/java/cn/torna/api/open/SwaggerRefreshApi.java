package cn.torna.api.open;

import cn.torna.common.bean.User;
import cn.torna.common.util.CopyUtil;
import cn.torna.dao.entity.Module;
import cn.torna.dao.entity.ModuleSwaggerConfig;
import cn.torna.service.ModuleService;
import cn.torna.service.ModuleSwaggerConfigService;
import cn.torna.service.dto.ImportSwaggerV2DTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author thc
 */
@Service
public class SwaggerRefreshApi {

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private SwaggerApi swaggerApi;

    @Autowired
    private ModuleSwaggerConfigService moduleSwaggerConfigService;

    /**
     * 刷新
     *
     * @param moduleId
     */
    public void refresh(Long moduleId, String ip, User user) {
        ModuleSwaggerConfig moduleSwaggerConfig = moduleSwaggerConfigService.getByModuleId(moduleId);
        if (moduleSwaggerConfig != null) {
            Module module = moduleService.getById(moduleId);
            ImportSwaggerV2DTO importSwaggerV2DTO = CopyUtil.copyBean(moduleSwaggerConfig, ImportSwaggerV2DTO::new);
            importSwaggerV2DTO.setUser(user);
            importSwaggerV2DTO.setIp(ip);
            importSwaggerV2DTO.setProjectId(module.getProjectId());
            swaggerApi.importSwagger(importSwaggerV2DTO, module);
        }
    }

}
