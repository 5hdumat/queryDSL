package study.querydsl.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberJpaRepositoryTest {
    @Autowired
    EntityManager em;
    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Test
    public void basicTest() throws Exception {
        //given
        Member member = new Member("member1", 10);
        memberJpaRepository.save(member);

        // when
        Member findMember = memberJpaRepository.findById(member.getId()).get();
        List<Member> findUsernameMembers = memberJpaRepository.findByUsernameQuerydsl(member.getUsername());
        List<Member> members = memberJpaRepository.findAllQuerydsl();

        // then
        assertThat(findMember).isEqualTo(member);
        assertThat(findUsernameMembers).containsExactly(member);
        assertThat(members).containsExactly(member);
    }

    @Test
    public void searchTest() throws Exception {
        //given
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

        // when
        MemberSearchCondition cond = new MemberSearchCondition();
        cond.setAgeGoe(35);
        cond.setAgeLoe(40);
        cond.setTeamName("teamB");

        List<MemberTeamDto> result = memberJpaRepository.search(cond);

        // then
        for (MemberTeamDto m : result) {
            System.out.println(m);
        }

        assertThat(result).extracting("username").containsExactly("member4");
    }

}