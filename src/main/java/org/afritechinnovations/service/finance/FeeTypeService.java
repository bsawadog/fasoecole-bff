package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.FeeTypeDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.finance.FeeFrequency;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FeeTypeService {

    private final FeeTypeRepository feeTypeRepository;

    public List<FeeTypeDto> findBySchool(Long schoolId) {
        return feeTypeRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public FeeTypeDto findById(Long id) {
        FeeType feeType = feeTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Type de frais introuvable: " + id));
        return toDto(feeType);
    }

    public FeeTypeDto create(FeeTypeDto dto) {
        FeeType feeType = FeeType.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .name(dto.getName())
                .amount(dto.getAmount())
                .frequency(dto.getFrequency() != null ? dto.getFrequency() : FeeFrequency.ONE_TIME)
                .build();
        return toDto(feeTypeRepository.save(feeType));
    }

    public FeeTypeDto update(Long id, FeeTypeDto dto) {
        FeeType feeType = feeTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Type de frais introuvable: " + id));
        feeType.setName(dto.getName());
        feeType.setAmount(dto.getAmount());
        feeType.setFrequency(dto.getFrequency());
        return toDto(feeTypeRepository.save(feeType));
    }

    public void delete(Long id) {
        feeTypeRepository.deleteById(id);
    }

    private FeeTypeDto toDto(FeeType feeType) {
        return FeeTypeDto.builder()
                .id(feeType.getId())
                .schoolId(feeType.getSchool().getId())
                .name(feeType.getName())
                .amount(feeType.getAmount())
                .frequency(feeType.getFrequency())
                .build();
    }
}
