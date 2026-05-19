package com.example.devlogapp.web;

import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.Mood;
import com.example.devlogapp.service.DevLogService;
import com.example.devlogapp.web.form.DevLogForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 회고 CRUD 컨트롤러.
 * PLAN.md §2 /logs/** 매핑 참조.
 * 비즈니스 로직 없이 폼 검증(@Valid) + 서비스 위임만.
 */
@Controller
@RequestMapping("/logs")
public class LogController {

    private final DevLogService devLogService;

    public LogController(DevLogService devLogService) {
        this.devLogService = devLogService;
    }

    /** GET /logs — 회고 목록 (날짜 내림차순). */
    @GetMapping
    public String list(Model model) {
        List<DevLog> logs = devLogService.findAll();
        model.addAttribute("logs", logs);
        return "logs/list";
    }

    /** GET /logs/new — 신규 작성 폼. */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("devLogForm", new DevLogForm());
        model.addAttribute("moods", Mood.values());
        model.addAttribute("isNew", true);
        return "logs/form";
    }

    /** POST /logs — 회고 저장 → /logs/{id}. */
    @PostMapping
    public String create(@Valid @ModelAttribute DevLogForm form, BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("moods", Mood.values());
            model.addAttribute("isNew", true);
            return "logs/form";
        }
        Mood mood = parseMood(form.getMood());
        DevLog created = devLogService.create(
                form.getDate(), form.getTitle(), form.tagList(),
                form.getWhatIDid(), form.getWhatILearned(),
                form.getProblems(), form.getTomorrow(), mood);
        return "redirect:/logs/" + created.getId();
    }

    /** GET /logs/{id} — 상세 화면. */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        DevLog log = devLogService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));
        model.addAttribute("log", log);
        return "logs/detail";
    }

    /** GET /logs/{id}/edit — 수정 폼. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        DevLog log = devLogService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));
        DevLogForm form = toForm(log);
        model.addAttribute("devLogForm", form);
        model.addAttribute("moods", Mood.values());
        model.addAttribute("isNew", false);
        model.addAttribute("logId", id);
        return "logs/form";
    }

    /** POST /logs/{id} — 수정 저장. */
    @PostMapping("/{id}")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute DevLogForm form, BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("moods", Mood.values());
            model.addAttribute("isNew", false);
            model.addAttribute("logId", id);
            return "logs/form";
        }
        Mood mood = parseMood(form.getMood());
        devLogService.update(id, form.getDate(), form.getTitle(), form.tagList(),
                form.getWhatIDid(), form.getWhatILearned(),
                form.getProblems(), form.getTomorrow(), mood);
        return "redirect:/logs/" + id;
    }

    /** POST /logs/{id}/delete — 삭제. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        devLogService.delete(id);
        return "redirect:/logs";
    }

    // ──────────────────────── helpers ────────────────────────

    private Mood parseMood(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Mood.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private DevLogForm toForm(DevLog log) {
        DevLogForm form = new DevLogForm();
        form.setDate(log.getDate());
        form.setTitle(log.getTitle());
        form.setTags(log.getTags() == null ? "" : String.join(", ", log.getTags()));
        form.setWhatIDid(log.getWhatIDid());
        form.setWhatILearned(log.getWhatILearned());
        form.setProblems(log.getProblems());
        form.setTomorrow(log.getTomorrow());
        form.setMood(log.getMood() == null ? "" : log.getMood().name());
        return form;
    }
}
