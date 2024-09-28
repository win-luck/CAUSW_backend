package net.causw.application.post;

import lombok.RequiredArgsConstructor;
import net.causw.adapter.persistence.board.Board;
import net.causw.adapter.persistence.circle.Circle;
import net.causw.adapter.persistence.circle.CircleMember;
import net.causw.adapter.persistence.comment.ChildComment;
import net.causw.adapter.persistence.comment.Comment;
import net.causw.adapter.persistence.form.Form;
import net.causw.adapter.persistence.form.FormQuestionOption;
import net.causw.adapter.persistence.form.FormQuestion;
import net.causw.adapter.persistence.repository.form.FormRepository;
import net.causw.adapter.persistence.repository.uuidFile.PostAttachImageRepository;
import net.causw.adapter.persistence.repository.vote.VoteRecordRepository;
import net.causw.adapter.persistence.uuidFile.joinEntity.PostAttachImage;
import net.causw.adapter.persistence.vote.Vote;
import net.causw.adapter.persistence.vote.VoteOption;
import net.causw.adapter.persistence.vote.VoteRecord;
import net.causw.application.dto.form.request.create.FormCreateRequestDto;
import net.causw.application.dto.form.response.FormResponseDto;
import net.causw.application.dto.form.response.OptionResponseDto;
import net.causw.application.dto.form.response.QuestionResponseDto;
import net.causw.application.dto.user.UserResponseDto;
import net.causw.application.dto.util.dtoMapper.*;
import net.causw.application.dto.vote.VoteOptionResponseDto;
import net.causw.application.dto.vote.VoteResponseDto;
import net.causw.application.pageable.PageableFactory;
import net.causw.adapter.persistence.post.FavoritePost;
import net.causw.adapter.persistence.post.LikePost;
import net.causw.adapter.persistence.post.Post;
import net.causw.adapter.persistence.repository.board.BoardRepository;
import net.causw.adapter.persistence.repository.board.FavoriteBoardRepository;
import net.causw.adapter.persistence.repository.circle.CircleMemberRepository;
import net.causw.adapter.persistence.repository.comment.ChildCommentRepository;
import net.causw.adapter.persistence.repository.comment.CommentRepository;
import net.causw.adapter.persistence.repository.comment.LikeChildCommentRepository;
import net.causw.adapter.persistence.repository.comment.LikeCommentRepository;
import net.causw.adapter.persistence.repository.post.FavoritePostRepository;
import net.causw.adapter.persistence.repository.post.LikePostRepository;
import net.causw.adapter.persistence.repository.post.PostRepository;
import net.causw.adapter.persistence.repository.user.UserRepository;
import net.causw.adapter.persistence.user.User;
import net.causw.adapter.persistence.uuidFile.UuidFile;
import net.causw.application.dto.comment.ChildCommentResponseDto;
import net.causw.application.dto.comment.CommentResponseDto;
import net.causw.application.dto.post.*;
import net.causw.application.dto.util.StatusUtil;
import net.causw.application.uuidFile.UuidFileService;
import net.causw.domain.aop.annotation.MeasureTime;
import net.causw.domain.exceptions.BadRequestException;
import net.causw.domain.exceptions.ErrorCode;
import net.causw.domain.exceptions.InternalServerException;
import net.causw.domain.exceptions.UnauthorizedException;
import net.causw.domain.model.enums.circle.CircleMemberStatus;
import net.causw.domain.model.enums.form.QuestionType;
import net.causw.domain.model.enums.form.RegisteredSemesterManager;
import net.causw.domain.model.enums.uuidFile.FilePath;
import net.causw.domain.model.enums.user.Role;
import net.causw.domain.model.util.MessageUtil;
import net.causw.domain.model.util.StaticValue;
import net.causw.domain.validation.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
@MeasureTime
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final CommentRepository commentRepository;
    private final ChildCommentRepository childCommentRepository;
    private final FavoriteBoardRepository favoriteBoardRepository;
    private final LikePostRepository likePostRepository;
    private final FavoritePostRepository favoritePostRepository;
    private final LikeCommentRepository likeCommentRepository;
    private final LikeChildCommentRepository likeChildCommentRepository;
    private final PageableFactory pageableFactory;
    private final Validator validator;
    private final UuidFileService uuidFileService;
    private final PostAttachImageRepository postAttachImageRepository;
    private final FormRepository formRepository;

    public PostResponseDto findPostById(User user, String postId) {
        Post post = getPost(postId);
        ValidatorBucket validatorBucket = initializeValidator(user, post.getBoard());
        validatorBucket.validate();
        return toPostResponseDtoExtended(post, user);
    }

    public BoardPostsResponseDto findAllPost(
            User user,
            String boardId,
            Integer pageNum
    ) {
        Set<Role> roles = user.getRoles();  // 사용자의 역할 가져오기
        Board board = getBoard(boardId);    // 게시판 정보 가져오기

        // 유효성 검사 초기화 및 실행
        ValidatorBucket validatorBucket = initializeValidator(user, board);
        validatorBucket.validate();

        // 동아리 리더 여부 확인
        boolean isCircleLeader = false;
        if (roles.contains(Role.LEADER_CIRCLE)) {
            isCircleLeader = getCircleLeader(board.getCircle()).getId().equals(user.getId());
        }

        if (isCircleLeader || roles.contains(Role.ADMIN) || roles.contains(Role.PRESIDENT)) {
            // 게시글 조회: 리더, 관리자, 회장인 경우 삭제된 게시글도 포함하여 조회
            return toBoardPostsResponseDto(
                    board,
                    roles,
                    isFavorite(user.getId(), board.getId()),
                    postRepository.findAllByBoard_IdOrderByCreatedAtDesc(boardId, pageableFactory.create(pageNum, StaticValue.DEFAULT_POST_PAGE_SIZE))
                            .map(this::toPostsResponseDto)
            );
        } else {
            // 일반 사용자는 삭제되지 않은 게시글만 조회
            return toBoardPostsResponseDto(
                    board,
                    roles,
                    isFavorite(user.getId(), board.getId()),
                    postRepository.findAllByBoard_IdAndIsDeletedOrderByCreatedAtDesc(boardId, pageableFactory.create(pageNum, StaticValue.DEFAULT_POST_PAGE_SIZE), false)
                            .map(this::toPostsResponseDto)
            );
        }
    }

    public BoardPostsResponseDto searchPost(
            User user,
            String boardId,
            String keyword,
            Integer pageNum
    ) {
        Set<Role> roles = user.getRoles();
        Board board = getBoard(boardId);

        ValidatorBucket validatorBucket = initializeValidator(user, board);
        validatorBucket
                .consistOf(TargetIsDeletedValidator.of(board.getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .validate();

        boolean isCircleLeader = false;
        if (roles.contains(Role.LEADER_CIRCLE)) {
            isCircleLeader = getCircleLeader(board.getCircle()).getId().equals(user.getId());
        }

        // 동아리장, Admin, 학생회장인 경우 삭제된 글 포함 검색. 그외의 경우 삭제되지 않는 글만 검색
        if (isCircleLeader || roles.contains(Role.ADMIN) || roles.contains(Role.PRESIDENT) || roles.contains(Role.VICE_PRESIDENT)) {
            return toBoardPostsResponseDto(
                    board,
                    roles,
                    isFavorite(user.getId(), board.getId()),
                    postRepository.findByTitleAndBoard_Id(keyword, boardId, pageableFactory.create(pageNum, StaticValue.DEFAULT_POST_PAGE_SIZE))
                            .map(this::toPostsResponseDto));
        } else {
            return toBoardPostsResponseDto(
                    board,
                    roles,
                    isFavorite(user.getId(), board.getId()),
                    postRepository.findByTitleBoard_IdAndDeleted(keyword, boardId, pageableFactory.create(pageNum, StaticValue.DEFAULT_POST_PAGE_SIZE), false)
                            .map(this::toPostsResponseDto));
        }
    }

    public BoardPostsResponseDto findAllAppNotice(User user, Integer pageNum) {
        Set<Role> roles = user.getRoles();
        Board board = boardRepository.findAppNotice().orElseThrow(
                () -> new BadRequestException(
                        ErrorCode.ROW_DOES_NOT_EXIST,
                        MessageUtil.BOARD_NOT_FOUND
                )
        );

        return toBoardPostsResponseDto(
                board,
                roles,
                isFavorite(user.getId(), board.getId()),
                postRepository.findAllByBoard_IdOrderByCreatedAtDesc(board.getId(), pageableFactory.create(pageNum, StaticValue.DEFAULT_POST_PAGE_SIZE))
                        .map(this::toPostsResponseDto));
    }

    @Transactional
    public PostResponseDto createPost(User creator, PostCreateRequestDto postCreateRequestDto, List<MultipartFile> attachImageList) {
        ValidatorBucket validatorBucket = ValidatorBucket.of();
        Set<Role> roles = creator.getRoles();

        Board board = getBoard(postCreateRequestDto.getBoardId());
        List<String> createRoles = new ArrayList<>(Arrays.asList(board.getCreateRoles().split(",")));
        if (board.getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }

        List<UuidFile> uuidFileList = (attachImageList == null || attachImageList.isEmpty()) ?
                new ArrayList<>() :
                attachImageList.stream()
                .map(multipartFile -> uuidFileService.saveFile(multipartFile, FilePath.POST))
                .toList();

        Post post = Post.of(
                postCreateRequestDto.getTitle(),
                postCreateRequestDto.getContent(),
                creator,
                postCreateRequestDto.getIsAnonymous(),
                postCreateRequestDto.getIsQuestion(),
                board,
                null,
                uuidFileList
        );

        validatorBucket
                .consistOf(UserStateValidator.of(creator.getState()))
                .consistOf(UserRoleIsNoneValidator.of(roles))
                .consistOf(PostNumberOfAttachmentsValidator.of(attachImageList))
                .consistOf(TargetIsDeletedValidator.of(board.getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .consistOf(UserRoleValidator.of(
                        roles,
                        createRoles.stream()
                                .map(Role::of)
                                .collect(Collectors.toSet())
                ));

        Optional<Circle> circles = Optional.ofNullable(board.getCircle());
        circles
                .filter(circle -> !roles.contains(Role.ADMIN) && !roles.contains(Role.PRESIDENT) && !roles.contains(Role.VICE_PRESIDENT))
                .ifPresent(
                        circle -> {
                            CircleMember member = getCircleMember(creator.getId(), circle.getId());

                            validatorBucket
                                    .consistOf(TargetIsDeletedValidator.of(circle.getIsDeleted(), StaticValue.DOMAIN_CIRCLE))
                                    .consistOf(CircleMemberStatusValidator.of(
                                            member.getStatus(),
                                            List.of(CircleMemberStatus.MEMBER)
                                    ));

                            if (roles.contains(Role.LEADER_CIRCLE) && !createRoles.contains("COMMON")) {
                                validatorBucket
                                        .consistOf(UserEqualValidator.of(
                                                getCircleLeader(circle).getId(),
                                                creator.getId()
                                        ));
                            }
                        }
                );
        validatorBucket
                .consistOf(ConstraintValidator.of(post, this.validator))
                .validate();

        return toPostResponseDtoExtended(postRepository.save(post), creator);
    }

    @Transactional
    public void createPostWithForm(
            User creator,
            PostCreateWithFormRequestDto postCreateWithFormRequestDto,
            List<MultipartFile> attachImageList
    ) {
        ValidatorBucket validatorBucket = ValidatorBucket.of();
        Set<Role> roles = creator.getRoles();

        Board board = getBoard(postCreateWithFormRequestDto.getBoardId());
        List<String> createRoles = new ArrayList<>(Arrays.asList(board.getCreateRoles().split(",")));
        if (board.getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }

        List<UuidFile> uuidFileList = (attachImageList == null || attachImageList.isEmpty()) ?
                new ArrayList<>() :
                attachImageList.stream()
                        .map(multipartFile -> uuidFileService.saveFile(multipartFile, FilePath.POST))
                        .toList();

        Form form = generateForm(postCreateWithFormRequestDto.getFormCreateRequestDto());

        Post post = Post.of(
                postCreateWithFormRequestDto.getTitle(),
                postCreateWithFormRequestDto.getContent(),
                creator,
                postCreateWithFormRequestDto.getIsAnonymous(),
                postCreateWithFormRequestDto.getIsQuestion(),
                board,
                form,
                uuidFileList
        );

        validatorBucket
                .consistOf(UserStateValidator.of(creator.getState()))
                .consistOf(UserRoleIsNoneValidator.of(roles))
                .consistOf(PostNumberOfAttachmentsValidator.of(attachImageList))
                .consistOf(TargetIsDeletedValidator.of(board.getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .consistOf(UserRoleValidator.of(
                        roles,
                        createRoles.stream()
                                .map(Role::of)
                                .collect(Collectors.toSet())
                ));

        Optional<Circle> circles = Optional.ofNullable(board.getCircle());
        circles
                .filter(circle -> !roles.contains(Role.ADMIN) && !roles.contains(Role.PRESIDENT) && !roles.contains(Role.VICE_PRESIDENT))
                .ifPresent(
                        circle -> {
                            CircleMember member = getCircleMember(creator.getId(), circle.getId());

                            validatorBucket
                                    .consistOf(TargetIsDeletedValidator.of(circle.getIsDeleted(), StaticValue.DOMAIN_CIRCLE))
                                    .consistOf(CircleMemberStatusValidator.of(
                                            member.getStatus(),
                                            List.of(CircleMemberStatus.MEMBER)
                                    ));

                            if (roles.contains(Role.LEADER_CIRCLE) && !createRoles.contains("COMMON")) {
                                validatorBucket
                                        .consistOf(UserEqualValidator.of(
                                                getCircleLeader(circle).getId(),
                                                creator.getId()
                                        ));
                            }
                        }
                );
        validatorBucket
                .consistOf(ConstraintValidator.of(post, this.validator))
                .validate();

        postRepository.save(post);
    }



    @Transactional
    public void deletePost(User deleter, String postId) {
        Post post = getPost(postId);
        Set<Role> roles = deleter.getRoles();

        ValidatorBucket validatorBucket = ValidatorBucket.of();
        if (post.getBoard().getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }
        validatorBucket
                .consistOf(UserStateValidator.of(deleter.getState()))
                .consistOf(UserRoleIsNoneValidator.of(roles))
                .consistOf(TargetIsDeletedValidator.of(post.getIsDeleted(), StaticValue.DOMAIN_POST));

        Optional<Circle> circles = Optional.ofNullable(post.getBoard().getCircle());
        circles
                .filter(circle -> !roles.contains(Role.ADMIN) && !roles.contains(Role.PRESIDENT) && !roles.contains(Role.VICE_PRESIDENT))
                .ifPresentOrElse(
                        circle -> {
                            CircleMember member = getCircleMember(deleter.getId(), circle.getId());

                            validatorBucket
                                    .consistOf(TargetIsDeletedValidator.of(circle.getIsDeleted(), StaticValue.DOMAIN_CIRCLE))
                                    .consistOf(CircleMemberStatusValidator.of(
                                            member.getStatus(),
                                            List.of(CircleMemberStatus.MEMBER)
                                    )).consistOf(ContentsAdminValidator.of(
                                            roles,
                                            deleter.getId(),
                                            post.getWriter().getId(),
                                            List.of(Role.LEADER_CIRCLE)
                                    ));

                            if (roles.contains(Role.LEADER_CIRCLE) && !post.getWriter().getId().equals(deleter.getId())) {
                                validatorBucket
                                        .consistOf(UserEqualValidator.of(
                                                getCircleLeader(circle).getId(),
                                                deleter.getId()
                                        ));
                            }
                        },
                        () -> validatorBucket
                                .consistOf(ContentsAdminValidator.of(
                                        roles,
                                        deleter.getId(),
                                        post.getWriter().getId(),
                                        List.of())
                                )
                );
        validatorBucket.validate();

        post.setIsDeleted(true);
    }

    @Transactional
    public PostResponseDto updatePost(
            User updater,
            String postId,
            PostUpdateRequestDto postUpdateRequestDto,
            List<MultipartFile> attachImageList
    ) {
        Set<Role> roles = updater.getRoles();
        Post post = getPost(postId);

        ValidatorBucket validatorBucket = initializeValidator(updater, post.getBoard());
        if (post.getBoard().getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }
        validatorBucket
                .consistOf(PostNumberOfAttachmentsValidator.of(attachImageList))
                .consistOf(TargetIsDeletedValidator.of(post.getBoard().getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .consistOf(TargetIsDeletedValidator.of(post.getIsDeleted(), StaticValue.DOMAIN_POST))
                .consistOf(ContentsAdminValidator.of(
                        roles,
                        updater.getId(),
                        post.getWriter().getId(),
                        List.of()
                ))
                .consistOf(ConstraintValidator.of(post, this.validator));
        validatorBucket.validate();


        // post는 이미지가 nullable임 -> 이미지 null로 요청 시 기존 이미지 삭제
        List<PostAttachImage> postAttachImageList = new ArrayList<>();

        if (!attachImageList.isEmpty()) {
            postAttachImageList = uuidFileService.updateFileList(
                    post.getPostAttachImageList().stream().map(PostAttachImage::getUuidFile).collect(Collectors.toList()),
                            attachImageList, FilePath.POST
                    ).stream()
                    .map(uuidFile -> PostAttachImage.of(post, uuidFile))
                    .toList();
        } else {
            uuidFileService.deleteFileList(post.getPostAttachImageList().stream().map(PostAttachImage::getUuidFile).collect(Collectors.toList()));
        }

        postAttachImageRepository.deleteAll(post.getPostAttachImageList());

        formRepository.delete(post.getForm());

        post.update(
                postUpdateRequestDto.getTitle(),
                postUpdateRequestDto.getContent(),
                null,
                postAttachImageList
        );

        return toPostResponseDtoExtended(post, updater);
    }

    @Transactional
    public void updatePostWithForm(
            User updater,
            String postId,
            PostUpdateWithFormRequestDto postUpdateWithFormRequestDto,
            List<MultipartFile> attachImageList
    ) {
        Set<Role> roles = updater.getRoles();
        Post post = getPost(postId);

        ValidatorBucket validatorBucket = initializeValidator(updater, post.getBoard());
        if (post.getBoard().getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }
        validatorBucket
                .consistOf(PostNumberOfAttachmentsValidator.of(attachImageList))
                .consistOf(TargetIsDeletedValidator.of(post.getBoard().getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .consistOf(TargetIsDeletedValidator.of(post.getIsDeleted(), StaticValue.DOMAIN_POST))
                .consistOf(ContentsAdminValidator.of(
                        roles,
                        updater.getId(),
                        post.getWriter().getId(),
                        List.of()
                ))
                .consistOf(ConstraintValidator.of(post, this.validator));
        validatorBucket.validate();


        // post는 이미지가 nullable임 -> 이미지 null로 요청 시 기존 이미지 삭제
        List<PostAttachImage> postAttachImageList = new ArrayList<>();

        if (!attachImageList.isEmpty()) {
            postAttachImageList = uuidFileService.updateFileList(
                            post.getPostAttachImageList().stream().map(PostAttachImage::getUuidFile).collect(Collectors.toList()),
                            attachImageList, FilePath.POST
                    ).stream()
                    .map(uuidFile -> PostAttachImage.of(post, uuidFile))
                    .toList();
        } else {
            uuidFileService.deleteFileList(post.getPostAttachImageList().stream().map(PostAttachImage::getUuidFile).collect(Collectors.toList()));
        }

        postAttachImageRepository.deleteAll(post.getPostAttachImageList());

        formRepository.delete(post.getForm());

        Form form = generateForm(postUpdateWithFormRequestDto.getFormCreateRequestDto());

        post.update(
                postUpdateWithFormRequestDto.getTitle(),
                postUpdateWithFormRequestDto.getContent(),
                form,
                postAttachImageList
        );
    }

    @Transactional
    public void restorePost(User restorer, String postId) {
        Set<Role> roles = restorer.getRoles();
        Post post = getPost(postId);

        ValidatorBucket validatorBucket = ValidatorBucket.of();
        if (post.getBoard().getCategory().equals(StaticValue.BOARD_NAME_APP_NOTICE)) {
            validatorBucket
                    .consistOf(UserRoleValidator.of(
                            roles,
                            Set.of()
                    ));
        }
        validatorBucket
                .consistOf(UserStateValidator.of(restorer.getState()))
                .consistOf(UserRoleIsNoneValidator.of(roles))
                .consistOf(TargetIsDeletedValidator.of(post.getBoard().getIsDeleted(), StaticValue.DOMAIN_BOARD))
                .consistOf(TargetIsNotDeletedValidator.of(post.getIsDeleted(), StaticValue.DOMAIN_POST));

        Optional<Circle> circles = Optional.ofNullable(post.getBoard().getCircle());
        circles
                .filter(circle -> !roles.contains(Role.ADMIN) && !roles.contains(Role.PRESIDENT) && !roles.contains(Role.VICE_PRESIDENT))
                .ifPresentOrElse(
                        circle -> {
                            CircleMember member = getCircleMember(restorer.getId(), circle.getId());

                            validatorBucket
                                    .consistOf(TargetIsDeletedValidator.of(circle.getIsDeleted(), StaticValue.DOMAIN_CIRCLE))
                                    .consistOf(CircleMemberStatusValidator.of(
                                            member.getStatus(),
                                            List.of(CircleMemberStatus.MEMBER)
                                    ))
                                    .consistOf(ContentsAdminValidator.of(
                                            roles,
                                            restorer.getId(),
                                            post.getWriter().getId(),
                                            List.of(Role.LEADER_CIRCLE)
                                    ));

                            if (roles.contains(Role.LEADER_CIRCLE) && !post.getWriter().getId().equals(restorer.getId())) {
                                validatorBucket
                                        .consistOf(UserEqualValidator.of(
                                                getCircleLeader(circle).getId(),
                                                restorer.getId()
                                        ));
                            }
                        },
                        () -> validatorBucket
                                .consistOf(ContentsAdminValidator.of(
                                        roles,
                                        restorer.getId(),
                                        post.getWriter().getId(),
                                        List.of()
                                ))
                );

        validatorBucket
                .consistOf(ContentsAdminValidator.of(
                        roles,
                        restorer.getId(),
                        post.getWriter().getId(),
                        List.of(Role.LEADER_CIRCLE)
                ))
                .validate();

        post.setIsDeleted(false);
    }

    @Transactional
    public void likePost(User user, String postId) {
        Post post = getPost(postId);

        ValidatorBucket validatorBucket = ValidatorBucket.of();
        validatorBucket
                .consistOf(UserStateIsDeletedValidator.of(post.getWriter().getState()))
                .validate();

        if (isPostAlreadyLike(user, postId)) {
            throw new BadRequestException(ErrorCode.ROW_ALREADY_EXIST, MessageUtil.POST_ALREADY_LIKED);
        }

        LikePost likePost = LikePost.of(post, user);
        likePostRepository.save(likePost);
    }

    @Transactional
    public void favoritePost(User user, String postId) {
        Post post = getPost(postId);

        //FIXME : Validator 리팩토링 통합 후 해당 검사 로직을 해당방식으로 수정.
        if (isPostDeleted(post)) {
            throw new BadRequestException(ErrorCode.TARGET_DELETED, MessageUtil.POST_DELETED);
        }

        FavoritePost favoritePost;
        if (isPostAlreadyFavorite(user, postId)) {
            favoritePost = getFavoritePost(user, postId);
            if (favoritePost.getIsDeleted()) {
                favoritePost.setIsDeleted(false);
            } else {
                throw new BadRequestException(ErrorCode.ROW_ALREADY_EXIST, MessageUtil.POST_ALREADY_FAVORITED);
            }
        } else {
            favoritePost = FavoritePost.of(post, user, false);
        }

        favoritePostRepository.save(favoritePost);
    }


    @Transactional
    public void cancelFavoritePost(User user, String postId) {
        Post post = getPost(postId);

        //FIXME : Validator 리팩토링 통합 후 해당 검사 로직을 해당방식으로 수정.
        if (isPostDeleted(post)) {
            throw new BadRequestException(ErrorCode.TARGET_DELETED, MessageUtil.POST_DELETED);
        }

        FavoritePost favoritePost = getFavoritePost(user, postId);
        if (favoritePost.getIsDeleted()) {
            throw new BadRequestException(ErrorCode.ROW_ALREADY_EXIST, MessageUtil.FAVORITE_POST_ALREADY_DELETED);
        } else {
            favoritePost.setIsDeleted(true);
        }

        favoritePostRepository.save(favoritePost);
    }

    private Boolean isPostAlreadyLike(User user, String postId) {
        return likePostRepository.existsByPostIdAndUserId(postId, user.getId());
    }

    private Boolean isPostAlreadyFavorite(User user, String postId) {
        return favoritePostRepository.existsByPostIdAndUserId(postId, user.getId());
    }

    private Boolean isPostDeleted(Post post) {
        return post.getIsDeleted();
    }

    private ValidatorBucket initializeValidator(User user, Board board) {
        Set<Role> roles = user.getRoles();
        ValidatorBucket validatorBucket = ValidatorBucket.of();
        validatorBucket
                .consistOf(UserStateValidator.of(user.getState()))
                .consistOf(UserRoleIsNoneValidator.of(roles));

        Optional<Circle> circles = Optional.ofNullable(board.getCircle());
        circles
                .filter(circle -> !roles.contains(Role.ADMIN) && !roles.contains(Role.PRESIDENT) && !roles.contains(Role.VICE_PRESIDENT))
                .ifPresent(
                        circle -> {
                            CircleMember member = getCircleMember(user.getId(), circle.getId());
                            validatorBucket
                                    .consistOf(TargetIsDeletedValidator.of(circle.getIsDeleted(), StaticValue.DOMAIN_CIRCLE))
                                    .consistOf(CircleMemberStatusValidator.of(
                                            member.getStatus(),
                                            List.of(CircleMemberStatus.MEMBER)
                                    ));
                        }
                );
        return validatorBucket;
    }

    private Form generateForm(FormCreateRequestDto formCreateRequestDto) {
        AtomicReference<Integer> questionNumber = new AtomicReference<>(1);

        List<FormQuestion> formQuestionList = Optional.ofNullable(formCreateRequestDto.getQuestionCreateRequestDtoList())
                .orElse(new ArrayList<>())
                .stream()
                .map(
                        questionCreateRequestDto -> {

                            AtomicReference<Integer> optionNumber = new AtomicReference<>(1);

                            List<FormQuestionOption> formQuestionOptionList = Optional.ofNullable(questionCreateRequestDto.getOptionCreateRequestDtoList())
                                    .orElse(new ArrayList<>())
                                    .stream()
                                    .map(
                                            optionCreateRequestDto -> FormQuestionOption.of(
                                                    optionNumber.getAndSet(optionNumber.get() + 1),
                                                    optionCreateRequestDto.getOptionText(),
                                                    null
                                            )
                                    ).toList();

                            if (questionCreateRequestDto.getQuestionType().equals(QuestionType.OBJECTIVE)) {
                                if (questionCreateRequestDto.getIsMultiple() == null ||
                                        questionCreateRequestDto.getOptionCreateRequestDtoList().isEmpty()
                                ) {
                                    throw new BadRequestException(
                                            ErrorCode.INVALID_PARAMETER,
                                            MessageUtil.INVALID_QUESTION_INFO
                                    );
                                }
                            } else {
                                if (questionCreateRequestDto.getIsMultiple() != null ||
                                        !questionCreateRequestDto.getOptionCreateRequestDtoList().isEmpty()
                                ) {
                                    throw new BadRequestException(
                                            ErrorCode.INVALID_PARAMETER,
                                            MessageUtil.INVALID_QUESTION_INFO
                                    );
                                }
                            }

                            FormQuestion formQuestion = FormQuestion.of(
                                    questionNumber.getAndSet(questionNumber.get() + 1),
                                    questionCreateRequestDto.getQuestionType(),
                                    questionCreateRequestDto.getQuestionText(),
                                    questionCreateRequestDto.getIsMultiple(),
                                    formQuestionOptionList,
                                    null
                            );

                            formQuestionOptionList.forEach(option -> option.setFormQuestion(formQuestion));

                            return formQuestion;
                        }
                ).toList();

        Form form = Form.createPostForm(
                formCreateRequestDto.getTitle(),
                formQuestionList,
                formCreateRequestDto.getIsAllowedEnrolled(),
                formCreateRequestDto.getIsAllowedEnrolled() ?
                        RegisteredSemesterManager.fromEnumList(
                                formCreateRequestDto.getEnrolledRegisteredSemesterList()
                        )
                        : null,
                formCreateRequestDto.getIsAllowedEnrolled() ?
                        formCreateRequestDto.getIsNeedCouncilFeePaid()
                        : false,
                formCreateRequestDto.getIsAllowedLeaveOfAbsence(),
                formCreateRequestDto.getIsAllowedLeaveOfAbsence() ?
                        RegisteredSemesterManager.fromEnumList(
                                formCreateRequestDto.getLeaveOfAbsenceRegisteredSemesterList()
                        )
                        : null,
                formCreateRequestDto.getIsAllowedGraduation()
        );

        formQuestionList.forEach(question -> question.setForm(form));

        return form;
    }


    // DtoMapper methods

    private BoardPostsResponseDto toBoardPostsResponseDto(Board board, Set<Role> userRoles, boolean isFavorite, Page<PostsResponseDto> post) {
        List<String> roles = Arrays.asList(board.getCreateRoles().split(","));
        Boolean writable = userRoles.stream()
                .map(Role::getValue)
                .anyMatch(roles::contains);
        return PostDtoMapper.INSTANCE.toBoardPostsResponseDto(
                board,
                userRoles,
                writable,
                isFavorite,
                post
        );
    }

    private PostsResponseDto toPostsResponseDto(Post post) {
        return PostDtoMapper.INSTANCE.toPostsResponseDto(
                post,
                postRepository.countAllCommentByPost_Id(post.getId()),
                getNumOfPostLikes(post),
                getNumOfPostFavorites(post),
                StatusUtil.isPostVote(post),
                StatusUtil.isPostForm(post)
        );
    }

    private PostResponseDto toPostResponseDtoExtended(Post post, User user) {
        return PostDtoMapper.INSTANCE.toPostResponseDtoExtended(
                postRepository.save(post),
                findCommentsByPostIdByPage(user, post, 0),
                postRepository.countAllCommentByPost_Id(post.getId()),
                getNumOfPostLikes(post),
                getNumOfPostFavorites(post),
                isPostAlreadyLike(user, post.getId()),
                isPostAlreadyFavorite(user, post.getId()),
                StatusUtil.isUpdatable(post, user, isPostHasComment(post.getId())),
                StatusUtil.isDeletable(post, user, post.getBoard(), isPostHasComment(post.getId())),
                StatusUtil.isPostForm(post) ? toFormResponseDto(post.getForm()) : null,
                StatusUtil.isPostVote(post) ? toVoteResponseDto(post.getVote(), user) : null,
                StatusUtil.isPostVote(post),
                StatusUtil.isPostForm(post)
        );
    }

    private Page<CommentResponseDto> findCommentsByPostIdByPage(User user, Post post, Integer pageNum) {
        return commentRepository.findByPost_IdOrderByCreatedAt(
                post.getId(),
                pageableFactory.create(pageNum, StaticValue.DEFAULT_COMMENT_PAGE_SIZE)
            ).map(comment -> toCommentResponseDto(comment, user, post.getBoard()));

    }

    private CommentResponseDto toCommentResponseDto(Comment comment, User user, Board board) {
        return CommentDtoMapper.INSTANCE.toCommentResponseDto(
                comment,
                childCommentRepository.countByParentComment_IdAndIsDeletedIsFalse(comment.getId()),
                getNumOfCommentLikes(comment),
                isCommentAlreadyLike(user, comment.getId()),
                comment.getChildCommentList().stream()
                        .map(childComment -> toChildCommentResponseDto(childComment, user, board))
                        .collect(Collectors.toList()),
                StatusUtil.isUpdatable(comment, user),
                StatusUtil.isDeletable(comment, user, board)
        );
    }

    private ChildCommentResponseDto toChildCommentResponseDto(ChildComment childComment, User user, Board board) {
        return CommentDtoMapper.INSTANCE.toChildCommentResponseDto(
                childComment,
                getNumOfChildCommentLikes(childComment),
                isChildCommentAlreadyLike(user, childComment.getId()),
                StatusUtil.isUpdatable(childComment, user),
                StatusUtil.isDeletable(childComment, user, board)
        );
    }

    private Long getNumOfPostLikes(Post post){
        return likePostRepository.countByPostId(post.getId());
    }

    private Long getNumOfPostFavorites(Post post){
        return favoritePostRepository.countByPostIdAndIsDeletedFalse(post.getId());
    }

    private Long getNumOfCommentLikes(Comment comment){
        return likeCommentRepository.countByCommentId(comment.getId());
    }

    private Long getNumOfChildCommentLikes(ChildComment childComment) {
        return likeChildCommentRepository.countByChildCommentId(childComment.getId());
    }

    private Boolean isFavorite(String userId, String boardId) {
        return favoriteBoardRepository.findByUser_Id(userId)
                .stream()
                .filter(favoriteBoard -> !favoriteBoard.getBoard().getIsDeleted())
                .anyMatch(favoriteboard -> favoriteboard.getBoard().getId().equals(boardId));
    }

    private Boolean isPostHasComment(String postId){
        return commentRepository.existsByPostIdAndIsDeletedFalse(postId);
    }

    private Boolean isCommentAlreadyLike(User user, String commentId) {
        return likeCommentRepository.existsByCommentIdAndUserId(commentId, user.getId());
    }

    private Boolean isChildCommentAlreadyLike(User user, String childCommentId) {
        return likeChildCommentRepository.existsByChildCommentIdAndUserId(childCommentId, user.getId());
    }

    private Post getPost(String postId) {
        return postRepository.findById(postId).orElseThrow(
                () -> new BadRequestException(
                        ErrorCode.ROW_DOES_NOT_EXIST,
                        MessageUtil.POST_NOT_FOUND
                )
        );
    }

    private User getUser(String userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> new BadRequestException(
                        ErrorCode.ROW_DOES_NOT_EXIST,
                        MessageUtil.USER_NOT_FOUND
                )
        );
    }

    private Board getBoard(String boardId) {
        return boardRepository.findById(boardId).orElseThrow(
                () -> new BadRequestException(
                        ErrorCode.ROW_DOES_NOT_EXIST,
                        MessageUtil.BOARD_NOT_FOUND
                )
        );
    }

    private CircleMember getCircleMember(String userId, String circleId) {
        return circleMemberRepository.findByUser_IdAndCircle_Id(userId, circleId).orElseThrow(
                () -> new UnauthorizedException(
                        ErrorCode.NOT_MEMBER,
                        MessageUtil.CIRCLE_APPLY_INVALID
                )
        );
    }

    private User getCircleLeader(Circle circle) {
        User leader = circle.getLeader().orElse(null);
        if (leader == null) {
            throw new InternalServerException(
                    ErrorCode.INTERNAL_SERVER,
                    MessageUtil.CIRCLE_WITHOUT_LEADER
            );
        }
        return leader;
    }

    private FavoritePost getFavoritePost(User user, String postId) {
        return favoritePostRepository.findByPostIdAndUserId(postId, user.getId()).orElseThrow(
                () -> new BadRequestException(
                        ErrorCode.ROW_DOES_NOT_EXIST,
                        MessageUtil.FAVORITE_POST_NOT_FOUND
                )
        );
    }

    private FormResponseDto toFormResponseDto(Form form) {
        return FormDtoMapper.INSTANCE.toFormResponseDto(
                form,
                form.getFormQuestionList().stream()
                        .map(this::toQuestionResponseDto)
                        .collect(Collectors.toList())
        );
    }

    private QuestionResponseDto toQuestionResponseDto(FormQuestion formQuestion) {
        return FormDtoMapper.INSTANCE.toQuestionResponseDto(
                formQuestion,
                formQuestion.getFormQuestionOptionList().stream()
                        .map(this::toOptionResponseDto)
                        .collect(Collectors.toList())
        );
    }

    private OptionResponseDto toOptionResponseDto(FormQuestionOption formQuestionOption) {
        return FormDtoMapper.INSTANCE.toOptionResponseDto(formQuestionOption);
    }

    private VoteResponseDto toVoteResponseDto(Vote vote, User user) {
        List<VoteOptionResponseDto> voteOptionResponseDtoList = vote.getVoteOptions().stream()
                .map(this::tovoteOptionResponseDto)
                .collect(Collectors.toList());
        return VoteDtoMapper.INSTANCE.toVoteResponseDto(
                vote,
                voteOptionResponseDtoList
                , StatusUtil.isVoteOwner(user, vote)
                , vote.isEnd()
                , voteRecordRepository.existsByVoteOption_VoteAndUser(vote, user)
        );
    }

    private VoteOptionResponseDto tovoteOptionResponseDto(VoteOption voteOption) {
        List<VoteRecord> voteRecords = voteRecordRepository.findAllByVoteOption(voteOption);
        List<UserResponseDto> userResponseDtos = voteRecords.stream()
                .map(voteRecord -> UserDtoMapper.INSTANCE.toUserResponseDto(voteRecord.getUser(), null, null))
                .collect(Collectors.toList());
        return VoteDtoMapper.INSTANCE.toVoteOptionResponseDto(voteOption, voteRecords.size(), userResponseDtos);
    }

}
