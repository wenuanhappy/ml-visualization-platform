package com.ctplatform.controller;

import com.ctplatform.dto.ApiResponse;
import com.ctplatform.dto.MlDatasetDto;
import com.ctplatform.dto.MlProjectionDto;
import com.ctplatform.dto.MlTrainRequest;
import com.ctplatform.dto.MlTrainingResponse;
import com.ctplatform.service.MlTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 机器学习模块接口（第 08 组新增）。
 *
 * <p>挂在 {@code /api/ml} 前缀下，不改动已有的 {@code /api/auth}、{@code /api/modules} 等接口。</p>
 */
@RestController
@RequestMapping("/api/ml")
public class MlModuleController {

    @Autowired
    private MlTrainingService mlTrainingService;

    /** 数据集列表 */
    @GetMapping("/datasets")
    public ApiResponse<List<MlDatasetDto.Summary>> listDatasets() {
        return mlTrainingService.listDatasets();
    }

    /** 数据集详情（含样本点） */
    @GetMapping("/datasets/{id}")
    public ApiResponse<MlDatasetDto> getDataset(@PathVariable("id") Long id) {
        return mlTrainingService.getDataset(id);
    }

    /** 上传 CSV 数据集 */
    @PostMapping("/datasets/upload")
    public ApiResponse<MlDatasetDto.Summary> uploadDataset(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return mlTrainingService.uploadDataset(name, description, file);
    }

    /** 新建训练会话 */
    @PostMapping("/sessions")
    public ApiResponse<MlTrainingResponse> createSession(@RequestBody MlTrainRequest request) {
        return mlTrainingService.createSession(request);
    }

    /** 查询会话状态 */
    @GetMapping("/sessions/{id}")
    public ApiResponse<MlTrainingResponse> getSession(@PathVariable("id") Long id) {
        return mlTrainingService.getSession(id);
    }

    /** 步进一步 */
    @PostMapping("/sessions/{id}/step")
    public ApiResponse<MlTrainingResponse> step(@PathVariable("id") Long id) {
        return mlTrainingService.step(id);
    }

    /** 投影到指定两个维度 */
    @GetMapping("/sessions/{id}/projection")
    public ApiResponse<MlProjectionDto> projection(@PathVariable("id") Long id,
                                                   @RequestParam("x") String x,
                                                   @RequestParam("y") String y) {
        return mlTrainingService.projection(id, x, y);
    }

    /** 多算法对比：把每个配置都训练到底，返回指标 */
    @PostMapping("/comparison")
    public ApiResponse<List<Map<String, Object>>> comparison(@RequestBody Map<String, Object> body) {
        return mlTrainingService.comparison(body);
    }

    /** 特征选择：只在选中的特征子集上训练 */
    @PostMapping("/feature-selection")
    public ApiResponse<MlTrainingResponse> featureSelection(@RequestBody Map<String, Object> body) {
        return mlTrainingService.featureSelection(body);
    }

    /** AI 助教（需要配置 LLM_API_KEY，未配置时返回提示） */
    @PostMapping("/assistant")
    public ApiResponse<Map<String, Object>> assistant(@RequestBody Map<String, Object> body) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        String context = String.valueOf(body.getOrDefault("context", ""));
        String answer = mlTrainingService.askAssistant(question, context);
        return ApiResponse.success(Map.of("answer", answer));
    }

    /** 复位 */
    @PostMapping("/sessions/{id}/reset")
    public ApiResponse<MlTrainingResponse> reset(@PathVariable("id") Long id) {
        return mlTrainingService.reset(id);
    }
}
