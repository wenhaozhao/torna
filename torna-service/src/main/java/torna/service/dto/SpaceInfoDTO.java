package torna.service.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import torna.common.support.IdCodec;

import java.util.Date;
import java.util.List;

/**
 * @author tanghc
 */
@Data
public class SpaceInfoDTO {

    @JSONField(serializeUsing = IdCodec.class, deserializeUsing = IdCodec.class)
    private Long id;

    /** 分组名称, 数据库字段：name */
    private String name;

    /**  数据库字段：creator_name */
    private String creatorName;

    /** 是否是个人默认空间，1：是，0：否, 数据库字段：is_default */
    private Byte isDefault;

    /** 空间管理员 */
    private List<UserInfoDTO> leaders;

    /**  数据库字段：gmt_create */
    private Date gmtCreate;

}
