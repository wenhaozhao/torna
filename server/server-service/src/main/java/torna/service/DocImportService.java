package torna.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.apache.commons.lang.BooleanUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import torna.common.bean.HttpTool;
import torna.common.bean.User;
import torna.common.enums.ParamStyleEnum;
import torna.common.exception.BizException;
import torna.common.util.DataIdUtil;
import torna.dao.entity.DocInfo;
import torna.dao.entity.DocParam;
import torna.dao.entity.Module;
import torna.manager.doc.DocParser;
import torna.manager.doc.IParam;
import torna.manager.doc.postman.Body;
import torna.manager.doc.postman.Item;
import torna.manager.doc.postman.Param;
import torna.manager.doc.postman.Postman;
import torna.manager.doc.postman.Request;
import torna.manager.doc.postman.Url;
import torna.manager.doc.swagger.DocBean;
import torna.manager.doc.swagger.DocItem;
import torna.manager.doc.swagger.DocModule;
import torna.manager.doc.swagger.DocParameter;
import torna.service.dto.DocItemCreateDTO;
import torna.service.dto.ImportPostmanDTO;
import torna.service.dto.ImportSwaggerDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author tanghc
 */
@Service
@Slf4j
public class DocImportService {

    @Autowired
    @Qualifier("swaggerDocParserV2")
    private DocParser<DocBean> docParserV2;

    @Autowired
    @Qualifier("swaggerDocParserV3")
    private DocParser<DocBean> docParserV3;

    @Autowired
    @Qualifier("postmanDocParser")
    private DocParser<Postman> postmanParser;

    @Autowired
    private DocInfoService docInfoService;

    @Autowired
    private DocParamService docParamService;

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private ModuleConfigService moduleConfigService;

    private static final Map<String, String> CONTEXT_TYPE_MAP = new HashMap<>();
    static {
        CONTEXT_TYPE_MAP.put("json", "application/json");
        CONTEXT_TYPE_MAP.put("xml", "application/xml");
        CONTEXT_TYPE_MAP.put("text", "text/plain");
    }


    /**
     * 导入swagger文档
     *
     * @param importSwaggerDTO importSwaggerDTO
     */
    @Transactional(rollbackFor = Exception.class)
    public Module importSwagger(ImportSwaggerDTO importSwaggerDTO) {
        String json;
        String url = importSwaggerDTO.getImportUrl();
        try {
            HttpTool httpTool = new HttpTool(importSwaggerDTO.getBasicAuthUsername(), importSwaggerDTO.getBasicAuthPassword());
            Response response = httpTool.requestForResponse(url, null, null, HttpTool.HTTPMethod.GET);
            String body = response.body().string();
            if (response.code() != HttpStatus.OK.value()) {
                throw new BizException(body);
            }
            json = body;
        } catch (IOException e) {
            log.error("导入swagger异常, url:{}", url, e);
            throw new BizException("导入异常, msg:" + e.getMessage());
        }
        JSONObject docRoot = JSON.parseObject(json, Feature.OrderedField, Feature.DisableCircularReferenceDetect);
        DocParser<DocBean> docParser = docRoot.containsKey("openapi") ? docParserV3 : docParserV2;
        DocBean docBean = docParser.parseJson(json);
        return this.saveDocToDb(docBean, importSwaggerDTO);
    }

    /**
     * 导入postman文档
     *
     * @param importPostmanDTO 导入配置
     * @return 返回模块
     */
    @Transactional(rollbackFor = Exception.class)
    public Module importPostman(ImportPostmanDTO importPostmanDTO) {
        String json = importPostmanDTO.getJson();
        Postman postman = postmanParser.parseJson(json);
        return this.savePostmanToDB(postman, importPostmanDTO);
    }

    private Module savePostmanToDB(Postman postman, ImportPostmanDTO importPostmanDTO) {
        return this.createPostmanModule(postman, importPostmanDTO);
    }

    private Module createPostmanModule(Postman postman, ImportPostmanDTO importPostmanDTO) {
        User user = importPostmanDTO.getUser();
        String title = postman.getInfo().getName();
        // 创建模块
        Module module = moduleService.createPostmanModule(importPostmanDTO, title);
        List<Item> items = postman.getItem();
        this.saveItems(items, null, module, user);
        return module;
    }

    private void saveItems(List<Item> items, DocInfo parent, Module module, User user) {
        for (Item item : items) {
            // 如果是文件夹
            if (item.isFolder()) {
                Long parentId = parent == null ? 0L : parent.getId();
                DocInfo folder = docInfoService.createDocFolderNoCheck(item.getName(), parentId, module.getId(), user);
                // 创建模块下的文档
                List<Item> subItems = item.getItem();
                this.saveItems(subItems, folder, module, user);
            } else {
                DocItemCreateDTO docItemCreateDTO = this.buildPostmanDocItemCreateDTO(item, parent, module, user);
                DocInfo docItem = docInfoService.createDocItem(docItemCreateDTO);
                // 创建参数
                List<Param> params = this.buildPostmanParams(item);
                this.savePostmanParams(params, docItem, docParameter -> {
                    return ParamStyleEnum.REQUEST;
                }, user);
            }
        }
    }

    private List<Param> buildPostmanParams(Item item) {
        List<Param> list = new ArrayList<>();
        Request request = item.getRequest();
        Url url = request.getUrl();
        List<Param> query = url.getQuery();
        if (query != null) {
            list.addAll(query);
        }
        Body body = request.getBody();
        if (body != null) {
            List<Param> params = this.parseBody(body);
            list.addAll(params);
        }
        return list;
    }

    private List<Param> parseBody(Body body) {
        String mode = body.getMode();
        switch (mode) {
            case "raw":
                String json = body.getRaw();
                JSONObject jsonObject = JSON.parseObject(json);
                return this.parseParams(jsonObject);
            case "urlencoded":
                return body.getUrlencoded();
            default:
                return Collections.emptyList();
        }
    }

    private List<Param> parseParams(JSONObject jsonObject) {
        List<Param> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            Param param = new Param();
            list.add(param);
            param.setKey(entry.getKey());
            param.setType("string");
            Object value = entry.getValue();
            if (value instanceof JSONObject) {
                param.setType("object");
                JSONObject valueObject = (JSONObject) value;
                List<Param> params = this.parseParams(valueObject);
                param.setChildren(params);
            } else if (value instanceof JSONArray) {
                param.setType("array");
                JSONArray array = (JSONArray) value;
                if (array.size() > 0) {
                    JSONObject el = array.getJSONObject(0);
                    List<Param> params = this.parseParams(el);
                    param.setChildren(params);
                }
            } else {
                param.setValue(String.valueOf(entry.getValue()));
            }
        }
        return list;
    }

    /**
     * 保存文档到数据库
     *
     * @param docBean
     * @param importSwaggerDTO
     */
    private Module saveDocToDb(DocBean docBean, ImportSwaggerDTO importSwaggerDTO) {
        User user = importSwaggerDTO.getUser();
        String title = docBean.getTitle();
        // 创建模块
        Module module = moduleService.createSwaggerModule(importSwaggerDTO, title);
        // 保存baseUrl
        moduleConfigService.setBaseUrl(module.getId(), docBean.getRequestUrl());
        // 创建文档分类
        List<DocModule> docModules = docBean.getDocModules();
        docModules.sort(Comparator.comparing(DocModule::getOrder));
        for (DocModule docModule : docModules) {
            DocInfo moduleDocInfo = docInfoService.createDocFolderNoCheck(docModule.getModule(), module.getId(), user);
            // 创建模块下的文档
            List<DocItem> items = docModule.getItems();
            for (DocItem item : items) {
                DocItemCreateDTO docItemCreateDTO = this.buildDocItemCreateDTO(item, moduleDocInfo, user);
                DocInfo docItem = docInfoService.createDocItem(docItemCreateDTO);
                // 创建参数
                List<DocParameter> requestParameters = item.getRequestParameters();
                this.saveParams(requestParameters, docItem, docParameter -> {
                    String in = ((DocParameter) docParameter).getIn();
                    if (in == null) {
                        in = "request";
                    }
                    switch (in) {
                        case "path":
                            return ParamStyleEnum.PATH;
                        case "header":
                            return ParamStyleEnum.HEADER;
                        default:
                            return ParamStyleEnum.REQUEST;
                    }
                }, user);
                List<DocParameter> responseParameters = item.getResponseParameters();
                this.saveParams(responseParameters, docItem, p -> ParamStyleEnum.RESPONSE, user);
            }
        }
        return module;
    }

    private void saveParams(
            List<DocParameter> parameters
            , DocInfo docItem
            , Function<IParam, ParamStyleEnum> styleEnumFunction
            , User user
    ) {
        if (CollectionUtils.isEmpty(parameters)) {
            return;
        }
        for (DocParameter parameter : parameters) {
            this.saveDocParam(parameter, docItem, 0, styleEnumFunction, user);
        }

    }

    private void savePostmanParams(
            List<Param> parameters
            , DocInfo docItem
            , Function<IParam, ParamStyleEnum> styleEnumFunction
            , User user
    ) {
        if (CollectionUtils.isEmpty(parameters)) {
            return;
        }
        for (Param parameter : parameters) {
            this.saveDocParam(parameter, docItem, 0, styleEnumFunction, user);
        }

    }

    private void saveDocParam(
            IParam docParameter
            , DocInfo docInfo
            , long parentId
            , Function<IParam, ParamStyleEnum> styleEnumFunction
            , User user
    ) {
        DocParam docParam = new DocParam();
        ParamStyleEnum styleEnum = styleEnumFunction.apply(docParameter);
        String dataId = DataIdUtil.getDocParamDataId(docInfo.getId(), parentId, styleEnum.getStyle(), docParameter.getName());
        docParam.setDataId(dataId);
        docParam.setName(docParameter.getName());
        docParam.setType(docParameter.getType());
        docParam.setRequired(BooleanUtils.toIntegerObject(docParameter.getRequired()).byteValue());
        docParam.setMaxLength(docParameter.getMaxLength());
        docParam.setExample(docParameter.getExample());
        docParam.setDescription(docParameter.getDescription());
        docParam.setDocId(docInfo.getId());
        docParam.setParentId(parentId);
        docParam.setStyle(styleEnum.getStyle());
        docParam.setCreateMode(user.getOperationModel());
        docParam.setModifyMode(user.getOperationModel());
        docParam.setCreatorId(user.getUserId());
        docParam.setModifierId(user.getUserId());
        // 保存操作
        DocParam savedDoc = docParamService.saveParam(docParam, user);

        // 处理子节点
        List<IParam> children = docParameter.getChildren();
        if (children != null) {
            for (IParam child : children) {
                this.saveDocParam(child, docInfo, savedDoc.getId(), styleEnumFunction, user);
            }
        }
    }


    private DocItemCreateDTO buildDocItemCreateDTO(DocItem item, DocInfo parent, User user) {
        DocItemCreateDTO docItemCreateDTO = new DocItemCreateDTO();
        docItemCreateDTO.setName(item.getSummary());
        docItemCreateDTO.setUrl(item.getPath());
        docItemCreateDTO.setContentType(Strings.join(item.getConsumes(), ','));
        docItemCreateDTO.setHttpMethod(item.getMethod());
        docItemCreateDTO.setDescription(item.getDescription());
        docItemCreateDTO.setModuleId(parent.getModuleId());
        docItemCreateDTO.setParentId(parent.getId());
        docItemCreateDTO.setUser(user);
        return docItemCreateDTO;
    }

    private DocItemCreateDTO buildPostmanDocItemCreateDTO(Item item, DocInfo parent, Module module, User user) {
        Request request = item.getRequest();
        String contentType = this.buildContentType(request);
        DocItemCreateDTO docItemCreateDTO = new DocItemCreateDTO();
        docItemCreateDTO.setName(item.getName());
        docItemCreateDTO.setUrl(request.getUrl().getFullUrl());
        docItemCreateDTO.setContentType(contentType);
        docItemCreateDTO.setHttpMethod(request.getMethod());
        docItemCreateDTO.setDescription(request.getDescription());
        docItemCreateDTO.setModuleId(module.getId());
        if (parent != null) {
            docItemCreateDTO.setParentId(parent.getId());
        }
        docItemCreateDTO.setUser(user);
        return docItemCreateDTO;
    }

    private String buildContentType(Request request) {
        Body body = request.getBody();
        if (body == null) {
            return "";
        }
        String mode = body.getMode();
        switch (mode) {
            case "raw":
                JSONObject options = body.getOptions();
                // json/xml/text
                String lang = Optional.ofNullable(options)
                        .flatMap(jsonObject -> Optional.ofNullable(jsonObject.getJSONObject("raw")))
                        .map(jsonObject -> jsonObject.getString("language"))
                        .orElse("text");
                return CONTEXT_TYPE_MAP.get(lang);
            case "urlencoded":
                return "application/x-www-form-urlencoded";
            default:
                return "";
        }
    }

}