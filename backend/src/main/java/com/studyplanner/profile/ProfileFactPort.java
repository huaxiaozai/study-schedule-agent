package com.studyplanner.profile;

import java.util.List;

import com.studyplanner.contract.ProfileFact;

public interface ProfileFactPort {

    List<ProfileFact> findAll();

    ProfileFact save(ProfileFact fact);
}
