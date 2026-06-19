package com.sellm.assessment;

import com.sellm.assessment.dto.AssessmentResponse;
import com.sellm.assessment.dto.SubmitAssessmentRequest;
import com.sellm.common.Result;
import com.sellm.scale.Answer;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentAppService appService;

    public AssessmentController(AssessmentAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<AssessmentResponse> submit(@RequestBody SubmitAssessmentRequest req) {
        List<Answer> answers = new ArrayList<>();
        if (req.getAnswers() != null) {
            for (SubmitAssessmentRequest.AnswerDto a : req.getAnswers()) {
                answers.add(new Answer(a.getItemId(), a.getScore()));
            }
        }
        Assessment saved = appService.submit(req.getChildId(), req.getScaleId(), answers);
        return Result.ok(new AssessmentResponse(saved.getId(), saved.getTotalScore(),
            saved.getBandLabel(), saved.getInterpretation()));
    }

    @GetMapping
    public Result<List<AssessmentResponse>> listByChild(@RequestParam Long childId) {
        List<AssessmentResponse> out = new ArrayList<>();
        for (Assessment a : appService.listByChild(childId)) {
            out.add(new AssessmentResponse(a.getId(), a.getTotalScore(),
                a.getBandLabel(), a.getInterpretation()));
        }
        return Result.ok(out);
    }
}
