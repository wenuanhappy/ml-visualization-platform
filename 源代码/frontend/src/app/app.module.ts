import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ExperimentComponent } from './experiment/experiment.component';
import { AlgorithmSelectorComponent } from './components/algorithm-selector/algorithm-selector.component';
import { DatasetSelectorComponent } from './components/dataset-selector/dataset-selector.component';
import { ParamPanelComponent } from './components/param-panel/param-panel.component';
import { VisualizationCanvasComponent } from './components/visualization-canvas/visualization-canvas.component';
import { ModelExplanationComponent } from './components/model-explanation/model-explanation.component';
import { EvaluationPanelComponent } from './components/evaluation-panel/evaluation-panel.component';
import { CaseLibraryComponent } from './components/case-library/case-library.component';
import { ModelComparisonComponent } from './components/model-comparison/model-comparison.component';
import { FeatureSelectionComponent } from './components/feature-selection/feature-selection.component';
import { AiAssistantComponent } from './components/ai-assistant/ai-assistant.component';
import { TrainingLogComponent } from './components/training-log/training-log.component';
import { LearningComponent } from './learning/learning.component';

@NgModule({
  declarations: [
    AppComponent,
    ExperimentComponent,
    LearningComponent,
    AlgorithmSelectorComponent,
    DatasetSelectorComponent,
    ParamPanelComponent,
    VisualizationCanvasComponent,
    ModelExplanationComponent,
    EvaluationPanelComponent,
    CaseLibraryComponent,
    ModelComparisonComponent,
    FeatureSelectionComponent,
    AiAssistantComponent,
    TrainingLogComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatSelectModule,
    MatInputModule,
    MatSliderModule,
    MatExpansionModule,
    MatTabsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDividerModule,
    MatTableModule,
    MatTooltipModule,
    MatCheckboxModule,
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
