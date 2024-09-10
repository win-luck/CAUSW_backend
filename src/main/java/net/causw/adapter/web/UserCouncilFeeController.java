package net.causw.adapter.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import net.causw.application.dto.userCouncilFee.CreateUserCouncilFeeRequestDto;
import net.causw.application.dto.userCouncilFee.UserCouncilFeeListResponseDto;
import net.causw.application.dto.userCouncilFee.UserCouncilFeeResponseDto;
import net.causw.application.userCouncilFee.UserCouncilFeeService;
import net.causw.config.security.userdetails.CustomUserDetails;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-council-fee")
public class UserCouncilFeeController {

    private final UserCouncilFeeService userCouncilFeeService;

    @GetMapping("/export/excel")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 엑셀 다운로드",
        description = "학생회비 엑셀 파일을 다운로드합니다.")
    public void exportUserCouncilFeeToExcel(
            HttpServletResponse response
    ) {
        userCouncilFeeService.exportUserCouncilFeeToExcel(response);
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 납부자 목록 조회",
        description = "학생회비 납부자 목록을 조회합니다.")
    public Page<UserCouncilFeeListResponseDto> getUserCouncilFeeList(
            @ParameterObject Pageable pageable
    ) {
        return userCouncilFeeService.getUserCouncilFeeList(pageable);
    }

    @GetMapping("/info/{userId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 납부자 상세 조회",
        description = "학생회비 납부자 상세 정보를 조회합니다.")
    public UserCouncilFeeResponseDto getUserCouncilFeeInfo(
            @PathVariable(value = "userId") String userId
    ) {
        return userCouncilFeeService.getUserCouncilFeeInfo(userId);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 납부자 등록",
        description = "학생회비 납부자를 등록합니다.")
    public void registerUserCouncilFee(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid CreateUserCouncilFeeRequestDto createUserCouncilFeeRequestDto
    ) {
        userCouncilFeeService.creatUserCouncilFee(userDetails.getUser(), createUserCouncilFeeRequestDto);
    }

    @PutMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 납부자 수정",
        description = "학생회비 납부자를 수정합니다.")
    public void updateUserCouncilFee(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader @Pattern(
                    regexp = "^[0-9a-fA-F]{32}$",
                    message = "대상 사용자 고유 id 값은 대시(-) 없이 32자리의 UUID 형식이어야 합니다."
            )
            @NotBlank(message = "대상 사용자 고유 id 값은 필수 입력 값입니다.") String userCouncilFeeId,
            @RequestBody @Valid CreateUserCouncilFeeRequestDto createUserCouncilFeeRequestDto
    ) {
        userCouncilFeeService.updateUserCouncilFee(userDetails.getUser(), userCouncilFeeId, createUserCouncilFeeRequestDto);
    }

    @DeleteMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학생회비 납부자 삭제",
        description = "학생회비 납부자를 삭제합니다.")
    public void deleteUserCouncilFee(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader String userId,
            @RequestHeader String userCouncilFeeId
    ) {
       userCouncilFeeService.deleteUserCouncilFee(userDetails.getUser(), userId, userCouncilFeeId);
    }

    @GetMapping("/getUserIdByStudentId")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "학번으로 사용자 id 조회",
        description = "학번으로 사용자 id를 조회합니다.")
    public String getUserIdByStudentId(
            @RequestHeader String studentId
    ) {
        return userCouncilFeeService.getUserIdByStudentId(studentId);
    }

    @GetMapping("/isCurrentSemesterApplied")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified() and " +
            "@securityService.adminOrPresidentOrVicePresident()")
    @Operation(summary = "특정 사용자가 현재 학생회비 적용 학기인지 여부 조회",
        description = "특정 사용자가 현재 학생회비 적용 학기인지 여부를 조회합니다.")
    public Boolean isCurrentSemesterApplied(
            @RequestHeader String userId
    ) {
        return userCouncilFeeService.isCurrentSemesterApplied(userId);
    }

    @GetMapping("/isCurrentSemesterApplied/self")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@securityService.activeAndNotNoneUser() and " +
            "@securityService.academicRecordCertified()")
    @Operation(summary = "본인이 현재 학생회비 적용 학기인지 여부 조회",
        description = "본인이 현재 학생회비 적용 학기인지 여부를 조회합니다.")
    public Boolean isCurrentSemesterAppliedBySelf(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return userCouncilFeeService.isCurrentSemesterAppliedBySelf(userDetails.getUser());
    }

}
