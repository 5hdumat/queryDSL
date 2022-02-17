package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.transaction.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
@Rollback(false)
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;

    /**
     * queryFactory는 스프링 컨테이너가 동시성 이슈를 해결해주므로,
     * Field Level로 가져가도 좋다.
     */
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member memberA = new Member("member1", 10, teamA);
        Member memberB = new Member("member2", 20, teamA);
        Member memberC = new Member("member3", 30, teamB);
        Member memberD = new Member("member4", 40, teamB);

        em.persist(memberA);
        em.persist(memberB);
        em.persist(memberC);
        em.persist(memberD);
    }

    @Test
    public void startJPQL() throws Exception {
        //given
        // beforeEach..

        // when
        Member findMember = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        // then
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void queryQuerydsl() throws Exception {
        //given

        /**
         * 아래와 같은 방식은 같은 테이블을 조인 해야하는 상황에서만 사용하는게 좋다.
         */
        // QMember qMember = new QMember("m1");

        // when
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // then
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 검색 조건 쿼리
     * <p>
     * - member.username.eq("member1") // username = 'member1'
     * - member.username.ne("member1") //username != 'member1'
     * - member.username.eq("member1").not() // username != 'member1'
     * <p>
     * - member.username.isNotNull() // 이름이 is not null
     * - member.age.in(10, 20) // age in (10,20)
     * - member.age.notIn(10, 20) // age not in (10, 20)
     * <p>
     * - member.age.between(10,30) // between 10, 30
     * - member.age.goe(30) // age >= 30
     * - member.age.gt(30) // age > 30
     * - member.age.loe(30) // age <= 30
     * - member.age.lt(30) // age < 30
     * <p>
     * - member.username.like("member%") // like 검색
     * - member.username.contains("member") // like ‘%member%’ 검색
     * - member.username.startsWith("member") //like ‘member%’ 검색
     */
    @Test
    public void search() throws Exception {
        //given

        // when
        Member findMember = queryFactory
                .selectFrom(member) // selct + from

                // 조건절 체이닝 방식
                .where(
                        member.username.eq("member1")
                                .and(member.age.eq(10))
                )
                .fetchOne();

        // then
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() throws Exception {
        //given

        // when
        Member findMember = queryFactory
                .selectFrom(member) // selct + from

                // 조건절 체이닝 방식 미사용
                .where(
                        member.username.eq("member1"),
                        member.age.between(10, 30)
                )
                .fetchOne();

        // then
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 결과 조회
     * <p>
     * fetch() : 리스트 조회, 데이터 없으면 빈 리스트 반환
     * fetchOne() : 단 건 조회 (결과가 없으면 : null / 결과가 둘 이상이면 : com.querydsl.core.NonUniqueResultException)
     * fetchFirst() : limit(1).fetchOne()
     * fetchResults() : 페이징 정보 포함, total count 쿼리 추가 실행
     * fetchCount() : count 쿼리로 변경해서 count 수 조회
     */

    @Test
    public void resultFetch() throws Exception {
        // fetch() : 리스트 조회, 데이터 없으면 빈 리스트 반환
        List<Member> fetch = queryFactory
                .select(member)
                .fetch();

        // fetchOne() : 단 건 조회
        Member fetchOne = queryFactory
                .select(member)
                .fetchOne();

        // fetchFirst() : limit(1).fetchOne()
        Member fetchFirst = queryFactory
                .select(member)
                .fetchFirst();

        /**
         * 실무에서는 보통 여러 테이블들이 다양한 연관관계로 조인되어 있어 페이징 쿼리가 복잡하다.
         * 하지만 count 쿼리의 경우 간단하게 가져올 수 있는 케이스가 있을수도 있으므로 성능 최적화가 가능하다면
         * count 전용 쿼리를 별도로 작성하는 것이 좋다.
         */
        // fetchResults() : 페이징 정보 포함, total count 쿼리 추가 실행
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();

        // fetchCount() : count 쿼리로 변경해서 count 수 조회
        long count = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 정렬
     * <p>
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 올림차순
     * <p>
     * 단, 2에서 회원 이름이 없으면 마지막에 출력
     */
    @Test
    public void sort() throws Exception {
        //given
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        // when
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        // then
        Member findMember1 = members.get(0);
        Member findMember2 = members.get(1);
        Member findMember3 = members.get(2);

        assertThat(findMember1.getUsername()).isEqualTo("member5");
        assertThat(findMember2.getUsername()).isEqualTo("member6");
        assertThat(findMember3.getUsername()).isNull();
    }

    /**
     * 페이징
     */
    @Test
    public void paging1() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    /**
     * 실무에서는 보통 여러 테이블들이 다양한 연관관계로 조인되어 있어 페이징 쿼리가 복잡하다.
     * 하지만 count 쿼리의 경우 간단하게 가져올 수 있는 케이스가 있을수도 있으므로 성능 최적화가 가능하다면
     * count 전용 쿼리를 별도로 작성하는 것이 좋다.
     */
    @Test
    public void paging2() throws Exception {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    /**
     * 집합 (Group)
     */

    @Test
    public void aggregation() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                ).from(member)
                .fetch();


        // when
        Tuple tuple = result.get(0);


        // then
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구하시오.
     */
    @Test
    public void group() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(
                        team.name,
                        member.age.avg()
                )
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                //                .having(team.name.eq("teamA"))
                .fetch();


        // when
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        // then
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 조인 (Join)
     */

    /**
     * 조인 (Join)
     * 팀 A에 소속된 모든 회원을 찾으시오.
     */
    @Test
    public void join() throws Exception {
        //given
        List<Member> members = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        // when
        for (Member member : members) {
            System.out.println(member);
        }

        // then
    }

    /**
     * 세타조인 (thetaJoin)
     * 세타조인을 사용하면 외부 조인(LEFT, RIGHT JOIN)이 불가능했었으나, 최근 hibernate(JPA 2.1부터 지원)가
     * on절을 지원하게 되면서 외부 조인이 가능해졌다.
     */
    @Test
    public void thetaJoin() throws Exception {
        //given
        em.persist(new Member("teamA", 10));
        em.persist(new Member("teamB", 20));
        em.persist(new Member("teamC", 30));


        // when
        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        // then
        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     */
    @Test
    public void joinOnFiltering() throws Exception {
        // given

        // when
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        // then
        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 예제) 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void joinOnNoRelation() throws Exception {
        //given
        em.persist(new Member("teamA", 10));
        em.persist(new Member("teamB", 20));
        em.persist(new Member("teamC", 30));


        // when
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)

                // 일반적인 조인
                // .join(member.team, team).on(member.username.eq(team.name))

                // 막조인(세타조인) 하이버네이트 5.1부터 서로 연관 관계가 없는 필드로 외부 조인하는 기능이 추가되었다.(내부도 가능)
                .join(team).on(member.username.eq(team.name))
                .fetch();

        // then
        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    /**
     * 한방 쿼리 활용 fetch join
     */

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        //given
        em.flush();
        em.clear();

        // when
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // then
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() throws Exception {
        //given
        em.flush();
        em.clear();

        // when
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // then
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /**
     * 서브쿼리
     * JPA를 활용한 서브쿼리의 한계점으로는 from 절에서 서브쿼리(인라인 뷰)를 지원하지 않는다.
     * JPA에서 지원하지 않으므로 당연히 Querydsl에서도 지원하지 않는다.
     * <p>
     * 예제) 나이가 가장 많은 회원 조회
     */

    @Test
    public void subQuery() throws Exception {
        //given

        // 기존의 member alias와 중복되면 안되므로 Qmember 객체를 새로 하나 더 생성해준다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();


        // when

        // then
        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    @Test
    public void subQueryGoe() throws Exception {
        //given

        // 기존의 member alias와 중복되면 안되므로 Qmember 객체를 새로 하나 더 생성해준다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();


        // when

        // then
        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    @Test
    public void subQueryIn() throws Exception {
        //given

        // 기존의 member alias와 중복되면 안되므로 Qmember 객체를 새로 하나 더 생성해준다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();


        // when

        // then
        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() throws Exception {
        //given
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(
                        member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();


        // when

        // then
        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    /**
     * case 문
     */

    @Test
    public void basicCase() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member.username,
                        member.age
                                .when(10).then("열살")
                                .when(20).then("스무살")
                                .otherwise("기타")
                )
                .from(member)
                .fetch();

        // when

        // then
        for (Tuple s : result) {
            System.out.println(s);
        }
    }

    @Test
    public void complexCase() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(
                        member.username,
                        new CaseBuilder()
                                .when(member.age.between(0, 20)).then("0 ~ 20살")
                                .when(member.age.between(21, 30)).then("21 ~ 30살")
                                .otherwise("기타")
                )
                .from(member)
                .fetch();

        // when

        // then
        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    /**
     * 상수, 문자 더하기
     */
    @Test
    public void constant() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(
                        member.username,
                        Expressions.constant("A")
                )
                .from(member)
                .fetch();


        // when

        // then
        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    @Test
    public void contcat() throws Exception {
        //given

        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .fetch();


        // when

        // then
        for (String s : result) {
            System.out.println(s);
        }
    }

    /**
     * - 프레젝션(Prosection, Select 대상을 지정하는 것)
     * - Tuple 자료형 (프로젝션 대상이 둘 이상일 때 사용)
     * tuple 자료형은 querydsl 스펙에 존재하는 자료형으로 해당 자료형을 서비스 계층까지 내보내는건 좋지 않다.
     * 이러현 구현체의 자료형은 최대한 DAO 계층안에서 처리하는게 좋다. (나중에 하부 기술을 querydsl에서 다른걸로 바꾸더라도 문제가 없도록)
     * <p>
     * DTO로 변환하여 내보내도록 하자.
     */

    @Test
    public void simplePorjection() throws Exception {
        //given
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();


        // when

        // then
        for (String s : result) {
            System.out.println(s);
        }
    }

    @Test
    public void tupleProjection() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();


        // when
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);

            System.out.println(username + "/" + age);
        }

        // then
    }

    @Test
    public void findDtoByJPQL() throws Exception {
        //given
        List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();


        // when
        for (MemberDto memberDto : resultList) {
            System.out.println(memberDto);
        }

        // then
    }

    /**
     * 프로젝션(Projection) - DTO 조회
     */

    // 프로퍼티 조회 (Setter를 통해서 값이 들어간다.)
    @Test
    public void findDtoBySetter() throws Exception {
        //given
        List<MemberDto> result = queryFactory
                .select(Projections.bean(
                        MemberDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();

        // when

        // then
        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    // 필드 조회 (Getter, Setter 없어도 됨)
    @Test
    public void findDtoByField() throws Exception {
        //given
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();


        // when

        // then
        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    // 생성자 조회 (DTO 필드와 순서, 타입이 맞아야 한다. 이름은 달라도된다.)
    @Test
    public void findDtoByConstructor() throws Exception {
        //given
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age
                ))
                .from(member)
                .fetch();


        // when

        // then
        for (UserDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    // 프로젝션을 활용한 DTO 조회는 필드명이 맞아 떨어져야 정상적으로 값이 입력된다.
    // 하지만 필드명을 바꿔서 조회하고싶을 땐 어떻게 해야 할까?
    // as를 이용하자.
    @Test
    public void findUserDto() throws Exception {
        QMember memberSub = new QMember("memberSub");

        //given
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),

                        // 회원 나이를 특정 값으로 고정하고싶다면?
                        // 주의점으로는 서브 쿼리는 ExpressionUtils의 as만 사용가능하다.
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        // when

        // then
        for (UserDto userDto : result) {
            System.out.println(userDto);
        }
    }

    /**
     * 프로젝션과 결과 반환 - @QueryProjection
     * Dto의 생성자에 QueryProjection 애노테이션을 붙여준 후 compileQuerydsl을 실행시켜주면 Q파일이 생성된다.
     * 이를 통해 생성자 호출을 하게되면 컴파일 시점에서 오류를 잡을 수 있다.
     * <p>
     * 하지만, dto가 querydsl 라이브러리에 의존하게된다는 단점이 있다.
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        //given
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        // when

        // then
        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    /**
     * 동적 쿼리
     * 파라미터에 따라 where 절을 동적으로 생성
     */

    @Test
    public void DynamicQueryBooleanBuilder() throws Exception {
        //given
        String usernameParam = "member1";
        Integer ageParam = 10;

        // when
        List<Member> result = searchMemberV1(usernameParam, ageParam);

        // then
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMemberV1(String usernameCond, Integer ageCond) {
        // 초깃값 지정도 가능하다.
        // BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond));

        BooleanBuilder builder = new BooleanBuilder();

        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * Where 다중 파라미터 사용 (실무 사용 권장)
     * [장점]
     * - where 조건에서 null 값은 무시
     * - 메서드를 다른 쿼리에서 재활용
     * - 코드 가독성 높아짐
     * - 자바 코드이므로 조건 메서드 조합 가능
     */
    @Test
    public void dynamicQueryWHereParam() throws Exception {
        //given
        String usernameParam = "member1";
        Integer ageParam = 10;

        // when
        List<Member> result = searchMemberV2(usernameParam, ageParam);

        // then
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMemberV2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                // .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    // 조건 메소드 조합해서 사용하기
    // 조합할 메서드 반환 타입을 Predicate -> BooleanExpression으로 변경해서 사용해야 한다.
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /**
     * 벌크 (Bulk) 연산
     * 변경 감지를 통해 건별로 엔티티를 수정하는 것은 네트워크 비용이 많이 들고, 오버헤드가 크다.
     * 조건에따라 모든 엔티티의 데이터를 수정하고자 한다면, 벌크 연산을 사용하자.
     */
    @Test
    public void bulkUpdate() throws Exception {
        //given

        // member1, 10 -> DB member1
        // member2, 20 -> DB member2
        // member3, 30 -> DB member3
        // member4, 40 -> DB member4

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.loe(20))
                .execute();

        // member1, 10 -> DB 비회원
        // member2, 20 -> DB 비회원
        // member3, 30 -> DB member3
        // member4, 40 -> DB member4

        /**
         [주의점]
         * 벌크연산은 영속성 컨택스트의 1차캐시에 저장되어있는 엔티티는 무시하고 곧바로 DB에 업데이트를 한다.
         * 이렇게되면 영속성 컨택스트와 DB 싱크가 맞지 않게 되는 문제점이 생긴다.
         *
         * 그러므로 벌크연산 후 항상 영속성 컨택스트를 clear해줘야 한다.
         */

        // when

        // 영속성 컨택스트의 1차 캐시에 저장되어 있는 값을 불러온다.
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        // then
        for (Member m : result) {
            System.out.println(m);
        }

        em.flush();
        em.clear();

        List<Member> result2 = queryFactory
                .selectFrom(member)
                .fetch();

        // DB 데이터를 불러온다.
        for (Member m : result2) {
            System.out.println(m);
        }

        assertThat(count).isEqualTo(2L);
    }

    @Test
    public void bulkAdd() throws Exception {
        //given
        // 회원의 모든 나이에 1살을 더하시오. .add()
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .set(member.username, member.username.append("1"))
                .execute();

        em.flush();
        em.clear();

        // when
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        // then
        for (Member m : result) {
            System.out.println(m);
        }

    }

    @Test
    public void bulkDelete() throws Exception {
        //given

        // 18살 이상 회원 모두 삭제
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        em.flush();
        em.clear();

        // when
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        // then
        for (Member m : result) {
            System.out.println(m);
        }
    }

    /**
     * SQL Function 호출하기
     * 주의점! SQL function은 JPA와 같이 Dialect(방언)에 등록된 내용만 호출할 수 있다.
     */
    @Test
    public void sqlFunction() throws Exception {
        //given
        /**
         * select function('replace', member1.username, ?1, ?2) from Member member1
         **/
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                                member.username, "member", "M"))
                .from(member)
                .fetch();


        // when

        // then
        for (String s : result) {
            System.out.println(s);
        }
    }

    /**
     * QueryDSL은 기본적으로 ANSI 표준으로 등록되어있는 기능들은 모두 내장하고 있다.
     */
    @Test
    public void sqlFucntion2() throws Exception {
        //given
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                //                .where(member.username.eq(
                //                        Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();


        // when

        // then
        for (String s : result) {
            System.out.println(s);
        }
    }
}
