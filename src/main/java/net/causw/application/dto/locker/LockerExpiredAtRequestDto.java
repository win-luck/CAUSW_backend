package net.causw.application.dto.locker;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LockerExpiredAtRequestDto {
    @ApiModelProperty(value = "만료일", example = "2024-09-01T11:41", required = true)
    private LocalDateTime expiredAt;
}
