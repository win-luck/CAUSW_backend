package net.causw.adapter.persistence.repository;

import io.lettuce.core.Value;
import net.causw.adapter.persistence.user.UserAcademicRecordApplication;
import net.causw.domain.model.enums.AcademicRecordRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAcademicRecordApplicationRepository extends JpaRepository<UserAcademicRecordApplication, String> {

    Page<UserAcademicRecordApplication> findAllByAcademicRecordRequestStatus(Pageable pageable, AcademicRecordRequestStatus academicRecordRequestStatus);

    List<UserAcademicRecordApplication> findAllByAcademicRecordRequestStatus(AcademicRecordRequestStatus academicRecordRequestStatus);

    Optional<UserAcademicRecordApplication> findDistinctTopByAcademicRecordRequestStatusOrderByCreatedAtDesc(AcademicRecordRequestStatus academicRecordRequestStatus);

}
