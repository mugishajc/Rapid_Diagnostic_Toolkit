package org.rdtoolkit.ui.provision;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.rdtoolkit.R;
import org.rdtoolkit.model.diagnostics.RdtDiagnosticProfile;
import org.rdtoolkit.util.ContextUtils;

import java.util.ArrayList;
import java.util.List;

public class ProvisionDefineFragment extends Fragment {

    private ProvisionViewModel mViewModel;

    public static ProvisionDefineFragment newInstance() {
        return new ProvisionDefineFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_provision_define, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(ProvisionViewModel.class);
        CheckBox cbxShowInstructions = ((CheckBox)view.findViewById(R.id.provision_cbx_instructions));

        mViewModel.getViewInstructions().observe(getViewLifecycleOwner(), value -> {
            cbxShowInstructions.setChecked(value);
        });

        cbxShowInstructions.setOnCheckedChangeListener((listener, checked) -> {
            mViewModel.setViewInstructions(checked);
        });

        mViewModel.getInstructionSets().observe(getViewLifecycleOwner(), value -> {
            if(value == null || value.size() == 0) {
                cbxShowInstructions.setVisibility(View.INVISIBLE);
            } else {
                cbxShowInstructions.setVisibility(View.VISIBLE);
            }
        });


        mViewModel.getProfileOptions().observe(getViewLifecycleOwner(), value ->{
            if (value == null) {
                view.findViewById(R.id.define_txt_type).setVisibility(View.VISIBLE);
                view.findViewById(R.id.define_spinner_test).setVisibility(View.GONE);
            } else {
                view.findViewById(R.id.define_txt_type).setVisibility(View.GONE);
                Spinner spinner = view.findViewById(R.id.define_spinner_test);
                spinner.setVisibility(View.VISIBLE);

                ArrayList<String> choices = new ArrayList<>();
                for (RdtDiagnosticProfile profile : value) {
                    choices.add(profile.readableName());
                }

                spinner.setAdapter(
                    new ArrayAdapter<>(this.getContext(),
                            R.layout.component_spinner_text, choices)
                );

                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mViewModel.getProfileOptions().getValue().get(i).id();
                        mViewModel.chooseTestProfile(mViewModel.getProfileOptions().getValue().get(i).id());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });
            }
        });

        mViewModel.getSelectedTestProfile().observe(getViewLifecycleOwner(), value -> {
            if (value == null) {
                ((TextView)view.findViewById(R.id.define_txt_type)).setText(getText(R.string.fragment_provision_test_name_none_selected));
                ((TextView)view.findViewById(R.id.define_txt_process)).setText("");
            } else {
                ((TextView)view.findViewById(R.id.define_txt_type)).setText(value.readableName());

                if(mViewModel.getProfileOptions().getValue() != null) {
                    ((Spinner)view.findViewById(R.id.define_spinner_test)).setSelection(getIndex(value));
                }

                ((TextView)view.findViewById(R.id.define_txt_process)).setText(new ContextUtils(requireContext()).getReadableTime(value.timeToResolve()));

                ImageView sampleView = view.findViewById(R.id.provision_sample_image);
                View sampleUnavailableView = view.findViewById(R.id.provision_sample_image_unavailable);

                int sampleImageResourceId = value.resourceId() == null ? 0 :
                        this.getResources().getIdentifier(value.resourceId(),
                        "drawable", getContext().getPackageName());

                if (sampleImageResourceId == 0) {
                    sampleView.setVisibility(View.INVISIBLE);
                    sampleUnavailableView.setVisibility(View.VISIBLE);
                } else {
                    sampleView.setImageResource(sampleImageResourceId);
                    sampleView.setVisibility(View.VISIBLE);
                    sampleUnavailableView.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private int getIndex(RdtDiagnosticProfile value) {
        List<RdtDiagnosticProfile> options = mViewModel.getProfileOptions().getValue();
        for (int i = 0 ; i < options.size() ; ++i) {
            if (value.id() == options.get(i).id()) {
                return i;
            }
        }
        return -1;
    }
}