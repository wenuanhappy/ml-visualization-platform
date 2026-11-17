package com.ctplatform.service;

import com.ctplatform.dto.ApiResponse;
import com.ctplatform.dto.MlDatasetDto;
import com.ctplatform.dto.MlProjectionDto;
import com.ctplatform.dto.MlTrainRequest;
import com.ctplatform.dto.MlTrainingResponse;

import java.util.List;

/**
 * 机器学习模块业务接口（第 08 组新增）。
 */
public interface MlTrainingService {

    /** 数据集列表（不含样本点） */
    ApiResponse<List<MlDatasetDto.Summary>> listDatasets();

    /** 数据集详情（含样本点） */
    ApiResponse<MlDatasetDto> getDataset(Long id);

    /** 新建训练会话 */
    ApiResponse<MlTrainingResponse> createSession(MlTrainRequest request);

    /** 查询会话当前状态 */
    ApiResponse<MlTrainingResponse> getSession(Long sessionId);

    /** 步进一步 */
    ApiResponse<MlTrainingResponse> step(Long sessionId);

    /** 把模型投影到指定的两个维度上 */
    ApiResponse<MlProjectionDto> projection(Long sessionId, String xName, String yName);

    /** 多算法对比 */
    ApiResponse<List<java.util.Map<String, Object>>> comparison(java.util.Map<String, Object> body);

    /** 上传 CSV 数据集（第一个非数值列作为类别，否则最后一列作为回归目标） */
    ApiResponse<MlDatasetDto.Summary> uploadDataset(String name, String description,
                                                    org.springframework.web.multipart.MultipartFile file);

    /** AI 助教问答 */
    String askAssistant(String question, String context);

    /** 特征子集训练 */
    ApiResponse<MlTrainingResponse> featureSelection(java.util.Map<String, Object> body);

    /** 复位到未开始状态 */
    ApiResponse<MlTrainingResponse> reset(Long sessionId);
}
